package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.crypto.SM2Util;
import com.example.common.crypto.SM3Util;
import com.example.common.crypto.SM4Util;
import com.example.im.config.MonitorKeyService;
import com.example.im.domain.MonitorSegment;
import com.example.im.domain.MonitorSession;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.repo.MonitorSegmentRepo;
import com.example.im.repo.MonitorSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

/**
 * 国密解密 + 落盘 (v2.2.97)
 *
 * <p>流程:
 * <ol>
 *   <li>校验 session 归属 (防止伪造 customerId)</li>
 *   <li>SM2 unwrap DEK</li>
 *   <li>SM4-GCM decrypt + tag 验证 (完整性)</li>
 *   <li>SM3 摘要 (审计)</li>
 *   <li>写盘 /var/data/monitor/{sessionId}/{idx}.seg.bin</li>
 *   <li>写 MonitorSegment 行 (LONGBLOB 也清空, 节省存储)</li>
 *   <li>更新 MonitorSession 头 (count + size + lastSegmentAt)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorUploadService {

    private final MonitorSegmentRepo segmentRepo;
    private final MonitorSessionRepo sessionRepo;
    private final ChatSessionRepo chatSessionRepo;
    private final MonitorKeyService keyService;

    @Value("${cs.monitor.storage-dir:/var/data/monitor}")
    private String storageDir;

    @Value("${cs.monitor.retention-days:180}")
    private int retentionDays;

    @Value("${cs.monitor.encrypt-at-rest:false}")
    private boolean encryptAtRest;

    @Transactional
    public MonitorSegment processSegment(Long sessionId,
                                         Integer segmentIdx,
                                         Integer durationMs,
                                         String ivB64,
                                         String wrappedDekB64,
                                         byte[] ciphertextWithTag,
                                         String expectedSm3) {
        if (!keyService.isEnabled()) {
            throw new ApiException(503, "录像模块未启用");
        }
        if (keyService.getPrivateKey() == null) {
            throw new ApiException(503, "录像私钥未加载, 请联系管理员");
        }

        // 1) 校验 session 归属 + 持久化 (拿 customerId / agent)
        var chatSessionOpt = chatSessionRepo.findById(sessionId);
        if (chatSessionOpt.isEmpty()) {
            throw new ApiException(404, "session 不存在");
        }
        var chatSession = chatSessionOpt.get();
        String customerId = chatSession.getCustomerId();
        String agent = chatSession.getAgentUsername();

        // 2) SM2 unwrap DEK
        byte[] dek;
        try {
            dek = SM2Util.unwrapDek(keyService.getPrivateKey(), wrappedDekB64);
            if (dek.length != SM4Util.KEY_LEN_BYTES) {
                throw new ApiException(400, "DEK 长度不对: " + dek.length);
            }
        } catch (Exception e) {
            throw new ApiException(400, "DEK 解包失败 (wrappedDek 不合法或与公钥不匹配): " + e.getMessage());
        }

        // 3) SM4-GCM decrypt + tag 验证
        byte[] iv;
        try {
            iv = Base64.getDecoder().decode(ivB64);
        } catch (Exception e) {
            throw new ApiException(400, "IV 不是 base64");
        }

        byte[] plaintext;
        try {
            plaintext = SM4Util.decrypt(dek, iv, ciphertextWithTag);
        } catch (Exception e) {
            throw new ApiException(400, "SM4-GCM 解密失败 / tag 校验不通过: " + e.getMessage());
        }

        // 4) SM3 校验 (可选, 客户端自愿)
        String sm3 = SM3Util.digestHex(plaintext);
        if (expectedSm3 != null && !expectedSm3.isBlank() && !expectedSm3.equalsIgnoreCase(sm3)) {
            // 不强阻, 因为 tag 已经过 GCM 校验; SM3 是额外审计
            log.warn("[Monitor] client/upload SM3 mismatch segment idx={} session={}", segmentIdx, sessionId);
        }

        // 5) 写盘
        Path dir = Paths.get(storageDir, String.valueOf(sessionId));
        Path file = dir.resolve(segmentIdx + ".seg.bin");
        try {
            Files.createDirectories(dir);
            Files.write(file, plaintext, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "写盘失败: " + e.getMessage());
        }

        // 6) 写 segment 行 (密文清空, 存 path + size + iv + wrappedDek 等元数据)
        MonitorSegment seg = new MonitorSegment();
        seg.setSessionId(sessionId);
        seg.setCustomerId(customerId);
        seg.setSegmentIdx(segmentIdx);
        // v2.3.0: hash 链 - 取上一段的 sm3 作为本段的 prev_hash
        String prevSm3 = null;
        // 查询最近一段 (idx < segmentIdx 中最大的)
        MonitorSegment prevSeg = segmentRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonitorSegment>()
                        .eq("session_id", sessionId)
                        .lt("segment_idx", segmentIdx)
                        .orderByDesc("segment_idx")
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (prevSeg != null) {
            prevSm3 = prevSeg.getSm3Hash();
        }

        seg.setDurationMs(durationMs);
        seg.setIvB64(ivB64);
        seg.setWrappedDekB64(wrappedDekB64);
        seg.setCiphertextBlob(new byte[0]);  // 清空, 不重复存
        seg.setSm3Hash(sm3);
        seg.setPrevSm3Hash(prevSm3);
        seg.setSizeBytes((long) plaintext.length);
        seg.setStoragePath(file.toString());
        seg.setKmsKeyId(keyService.getKeyFingerprint());
        seg.setUploadTs(LocalDateTime.now(ZoneOffset.UTC));
        segmentRepo.insert(seg);

        // 7) upsert session 头
        MonitorSession ms = sessionRepo.selectById(sessionId);
        if (ms == null) {
            ms = new MonitorSession();
            ms.setSessionId(sessionId);
            ms.setCustomerId(customerId);
            ms.setAgentUsername(agent);
            ms.setStartedAt(seg.getUploadTs());
            ms.setSegmentCount(1);
            ms.setTotalBytes(seg.getSizeBytes());
            ms.setStatus("RECORDING");
            ms.setRetentionUntil(LocalDateTime.now().plusDays(retentionDays));
            ms.setSm3ChainRoot(sm3);   // v2.3.0: 首段 sm3 作为链根
            sessionRepo.insert(ms);
        } else {
            ms.setSegmentCount(ms.getSegmentCount() + 1);
            ms.setTotalBytes(ms.getTotalBytes() + seg.getSizeBytes());
            ms.setLastSegmentAt(seg.getUploadTs());
            ms.setStatus("RECORDING");
            sessionRepo.updateById(ms);
        }

        log.info("[Monitor] ✓ session={} idx={} size={}B sm3={} path={}",
                sessionId, segmentIdx, plaintext.length, sm3.substring(0, 12), file);

        // 安全清理 DEK
        java.util.Arrays.fill(dek, (byte) 0);
        java.util.Arrays.fill(plaintext, (byte) 0);

        return seg;
    }

    /**
     * session 结束时调一次 (前端 /api/monitor/end 或后端检测到 chatSession ENDED)
     */
    @Transactional
    public void endSession(Long sessionId) {
        MonitorSession ms = sessionRepo.selectById(sessionId);
        if (ms == null) return;
        ms.setStatus("ENDED");
        sessionRepo.updateById(ms);
    }

    /**
     * 查询 session 录像元信息 (admin / 该 session agent 可调)
     */
    public MonitorSession getBySession(Long sessionId) {
        return sessionRepo.selectById(sessionId);
    }

    public List<MonitorSegment> listSegments(Long sessionId) {
        return segmentRepo.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MonitorSegment>()
                        .eq("session_id", sessionId)
                        .orderByAsc("segment_idx"));
    }
}
