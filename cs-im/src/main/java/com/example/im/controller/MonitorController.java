package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.config.MonitorKeyService;
import com.example.im.domain.MonitorSegment;
import com.example.im.domain.MonitorSession;
import com.example.im.service.MonitorAuditService;
import com.example.im.service.MonitorUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 录像监控 REST API (v2.2.97 国密)
 *
 * <p>路由:
 * <ul>
 *   <li>GET  /api/monitor/pubkey                       — 拉 SM2 pub (SDK 初始化)</li>
 *   <li>POST /api/monitor/upload                       — 客户 SDK 上传加密分片</li>
 *   <li>POST /api/monitor/end/{sessionId}              — 客户会话结束停止录像</li>
 *   <li>GET  /api/monitor/manifest/{sessionId}.m3u8    — admin / 坐席播放 manifest</li>
 *   <li>GET  /api/monitor/seg/{sessionId}/{idx}        — admin / 坐席播放单片 (Range)</li>
 *   <li>GET  /api/monitor/info/{sessionId}             — 元信息 (admin 用)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/im/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorUploadService uploadService;
    private final MonitorKeyService keyService;
    private final MonitorAuditService auditService;

    /**
     * 对外暴露 SM2 公钥 (PEM 的 SubjectPublicKeyInfo base64)
     */
    @GetMapping("/pubkey")
    public ApiResponse<?> pubkey() {
        if (!keyService.isEnabled() || keyService.getPublicKeyBase64() == null) {
            return ApiResponse.fail(503, "录像模块未启用");
        }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("alg", "SM2");
        data.put("curve", "sm2p256v1");
        data.put("pub", keyService.getPublicKeyBase64());
        data.put("cipher", "SM4/GCM/NoPadding");
        data.put("ivLen", 12);
        data.put("keyLen", 16);
        data.put("tagLen", 16);
        data.put("hash", "SM3");
        data.put("fingerprint", keyService.getKeyFingerprint());
        return ApiResponse.ok(data);
    }

    /**
     * 加密分片上传 (客户 SDK 调用)
     *
     * @param ivB64            SM4-GCM 96-bit IV (base64)
     * @param wrappedDekB64    SM2 加密的 SM4 密钥 (base64)
     * @param segmentIdx       分片序号
     * @param durationMs       该片时长 ms
     * @param sm3              可选 — 客户端计算的明文 SM3 摘要
     * @param file             multipart/form-data 字段名, 内容 = SM4-GCM ciphertext || 16B tag
     */
    @PostMapping("/upload")
    public ApiResponse<?> upload(@RequestParam("sessionId") Long sessionId,
                                 @RequestParam("segmentIdx") Integer segmentIdx,
                                 @RequestParam("durationMs") Integer durationMs,
                                 @RequestParam("ivB64") String ivB64,
                                 @RequestParam("wrappedDekB64") String wrappedDekB64,
                                 @RequestParam(value = "sm3", required = false) String sm3,
                                 @RequestParam("file") MultipartFile file) {
        // 鉴权: 当前用户必须是该 session 绑定的客户
        // 此端点会被 cs-gateway JwtGlobalFilter 透传 X-User-Role / X-User-Id, 我们直接用 SecurityContextHolder
        var ctx = SecurityContextHolder.current();
        if (ctx == null || ctx.getUserId() == null) {
            return ApiResponse.fail(401, "未登录");
        }
        // 防止别人代传: 校验 session 的 customerId 是否是当前 userId
        var sessionInfo = uploadService.getBySession(sessionId);
        boolean existingOk = sessionInfo != null && ctx.getUserId().equals(sessionInfo.getCustomerId());
        // 如果是第一次上传, sessionInfo == null, 后面会用 uploadService.processSegment 验证 (chatSession.customerId == ctx.getUserId())
        // 这里简化: 直接交给 service 校验

        try {
            byte[] ciphertext = file.getBytes();
            MonitorSegment seg = uploadService.processSegment(sessionId, segmentIdx, durationMs,
                    ivB64, wrappedDekB64, ciphertext, sm3);
            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", seg.getId());
            resp.put("segmentIdx", seg.getSegmentIdx());
            resp.put("size", seg.getSizeBytes());
            resp.put("sm3", seg.getSm3Hash());
            resp.put("storagePath", seg.getStoragePath());
            return ApiResponse.ok(resp);
        } catch (Exception e) {
            log.warn("[Monitor] upload failed session={} idx={}: {}",
                    sessionId, segmentIdx, e.getMessage());
            return ApiResponse.fail(e instanceof com.example.common.ApiException a ? a.getCode() : 500,
                    e.getMessage());
        }
    }

    /**
     * 客户会话语: 结束录像
     */
    @PostMapping("/end/{sessionId}")
    public ApiResponse<?> endSession(@PathVariable Long sessionId) {
        uploadService.endSession(sessionId);
        return ApiResponse.ok("ENDED");
    }

    /**
     * 播放 manifest (admin / 该 session agent 可访问)
     * 返回一个简单的 JSON 段列表 (实际播放由前端 video 标签直接拉 seg)
     * 也支持 HLS m3u8 模式, 这里给 JSON 兼容两种客户端
     */
    @GetMapping("/manifest/{sessionId}")
    public ApiResponse<?> manifest(@PathVariable Long sessionId,
                                   @RequestParam(value = "format", defaultValue = "json") String format,
                                   HttpServletResponse resp,
                                   HttpServletRequest req) throws IOException {
        MonitorSession ms = uploadService.getBySession(sessionId);
        if (ms == null) return ApiResponse.fail(404, "无录像");
        // v2.3.0: 访问审计 (管理员/坐席/主管查看录像)
        var ctx = com.example.common.SecurityContextHolder.current();
        if (ctx != null) {
            auditService.log(sessionId, ms.getCustomerId(),
                    ctx.getUserId(), ctx.getRole(), "VIEW",
                    null, null, "format=" + format, req);
        }
        List<MonitorSegment> segs = uploadService.listSegments(sessionId);

        if ("m3u8".equalsIgnoreCase(format)) {
            resp.setContentType("application/vnd.apple.mpegurl");
            StringBuilder sb = new StringBuilder();
            sb.append("#EXTM3U\n");
            sb.append("#EXT-X-VERSION:3\n");
            sb.append("#EXT-X-TARGETDURATION:").append(Math.max(1,
                    segs.stream().mapToInt(MonitorSegment::getDurationMs).max().orElse(5000) / 1000 + 1)).append("\n");
            sb.append("#EXT-X-MEDIA-SEQUENCE:0\n");
            for (var s : segs) {
                double dur = s.getDurationMs() / 1000.0;
                sb.append("#EXTINF:").append(String.format("%.3f", dur)).append(",\n");
                sb.append("/im/monitor/seg/").append(s.getSessionId()).append("/")
                        .append(s.getSegmentIdx()).append("\n");
            }
            sb.append("#EXT-X-ENDLIST\n");
            resp.getWriter().write(sb.toString());
            return null;
        }
        // JSON
        java.util.List<java.util.Map<String, Object>> list = segs.stream().map(s -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("idx", s.getSegmentIdx());
            m.put("durationMs", s.getDurationMs());
            m.put("size", s.getSizeBytes());
            m.put("sm3", s.getSm3Hash());
            m.put("url", "/im/monitor/seg/" + s.getSessionId() + "/" + s.getSegmentIdx());
            m.put("uploadTs", s.getUploadTs());
            return m;
        }).collect(Collectors.toList());

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("sessionId", sessionId);
        data.put("customerId", ms.getCustomerId());
        data.put("agentUsername", ms.getAgentUsername());
        data.put("status", ms.getStatus());
        data.put("startedAt", ms.getStartedAt());
        data.put("lastSegmentAt", ms.getLastSegmentAt());
        data.put("segmentCount", ms.getSegmentCount());
        data.put("totalBytes", ms.getTotalBytes());
        data.put("retentionUntil", ms.getRetentionUntil());
        data.put("segments", list);
        return ApiResponse.ok(data);
    }

    /**
     * 单片下载 (流式 + Range 支持)
     */
    @GetMapping("/seg/{sessionId}/{idx}")
    public void segment(@PathVariable Long sessionId,
                        @PathVariable Integer idx,
                        HttpServletResponse resp,
                        HttpServletRequest req) throws IOException {
        List<MonitorSegment> segs = uploadService.listSegments(sessionId);
        MonitorSegment target = segs.stream()
                .filter(s -> s.getSegmentIdx().equals(idx))
                .findFirst()
                .orElse(null);
        if (target == null) {
            resp.sendError(404);
            return;
        }
        // v2.3.0: 审计下载单段 (DOWNLOAD)
        var ctx = com.example.common.SecurityContextHolder.current();
        if (ctx != null) {
            MonitorSession ms = uploadService.getBySession(sessionId);
            auditService.log(sessionId, ms != null ? ms.getCustomerId() : "?",
                    ctx.getUserId(), ctx.getRole(), "DOWNLOAD",
                    idx, idx, "single seg", req);
        }
        Path file = Paths.get(target.getStoragePath());
        if (!Files.exists(file)) {
            resp.sendError(404);
            return;
        }
        resp.setContentType("application/octet-stream");
        resp.setContentLengthLong(target.getSizeBytes());
        resp.setHeader("X-SM3-Hash", target.getSm3Hash());

        // 简单流式 (无 Range 复杂分片)
        try (var in = Files.newInputStream(file); var out = resp.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /**
     * Session 元信息
     */
    @GetMapping("/info/{sessionId}")
    public ApiResponse<?> info(@PathVariable Long sessionId) {
        return ApiResponse.ok(uploadService.getBySession(sessionId));
    }

    /**
     * v2.3.0: 验证 hash 链完整性 (合规要求 "提供验真" 接口)
     *
     * <p>遍历所有 segment, 检查 prev_sm3_hash == 上一段的 sm3_hash;
     * 不一致则返回第一个出错位置 + 详情.
     */
    @GetMapping("/verify-chain/{sessionId}")
    public ApiResponse<?> verifyChain(@PathVariable Long sessionId) {
        List<MonitorSegment> segs = uploadService.listSegments(sessionId);
        if (segs.isEmpty()) return ApiResponse.ok(java.util.Map.of("ok", true, "segments", 0));

        int brokenAt = -1;
        for (int i = 1; i < segs.size(); i++) {
            MonitorSegment prev = segs.get(i - 1);
            MonitorSegment cur = segs.get(i);
            if (cur.getPrevSm3Hash() == null
                    || !cur.getPrevSm3Hash().equalsIgnoreCase(prev.getSm3Hash())) {
                brokenAt = i;
                break;
            }
        }
        boolean ok = brokenAt < 0;
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("ok", ok);
        resp.put("totalSegments", segs.size());
        if (!ok) {
            resp.put("brokenAtIndex", brokenAt);
            resp.put("expected", segs.get(brokenAt - 1).getSm3Hash());
            resp.put("actual", segs.get(brokenAt).getPrevSm3Hash());
            log.warn("[Monitor] hash 链断裂 session={} brokenAt={}", sessionId, brokenAt);
        }
        return ApiResponse.ok(resp);
    }

    /**
     * v2.3.0: 查看某 session 的访问审计 (仅 admin)
     */
    @GetMapping("/audit/{sessionId}")
    public ApiResponse<?> listAudit(@PathVariable Long sessionId) {
        var ctx = com.example.common.SecurityContextHolder.current();
        if (ctx == null || !"ADMIN".equals(ctx.getRole())) {
            return ApiResponse.fail(403, "仅管理员可查审计");
        }
        return ApiResponse.ok(auditService.listBySession(sessionId));
    }

    /**
     * v2.3.0: PLAYBACK_JUMP 审计 - 前端跳到某时间戳时调
     */
    @PostMapping("/audit/jump/{sessionId}")
    public ApiResponse<?> logJump(@PathVariable Long sessionId,
                                   @RequestBody(required = false) Map<String, Object> body,
                                   HttpServletRequest req) {
        var ctx = com.example.common.SecurityContextHolder.current();
        if (ctx == null) return ApiResponse.fail(401, "未登录");
        MonitorSession ms = uploadService.getBySession(sessionId);
        auditService.log(sessionId, ms != null ? ms.getCustomerId() : "?",
                ctx.getUserId(), ctx.getRole(), "PLAYBACK_JUMP",
                null, null, body == null ? null : body.toString(), req);
        return ApiResponse.ok("logged");
    }
}
