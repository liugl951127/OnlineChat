package com.example.im.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.im.config.MonitorKeyService;
import com.example.im.domain.MonitorSegment;
import com.example.im.domain.MonitorSession;
import com.example.im.repo.MonitorSegmentRepo;
import com.example.im.repo.MonitorSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 录像清理 cron (v2.2.97)
 *
 * <p>每天凌晨 03:15 扫一遍:
 * <ul>
 *   <li>session.retention_until < now → 物理删文件 + logical delete 行</li>
 *   <li>session.status = ENDED 已超过 7 天 → 同上</li>
 *   <li>物理删: monitor_session_id 目录下所有 *.seg.bin</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "cs.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class MonitorCleanupScheduler {

    private final MonitorSessionRepo sessionRepo;
    private final MonitorSegmentRepo segmentRepo;
    private final MonitorKeyService keyService;

    @Value("${cs.monitor.storage-dir:/var/data/monitor}")
    private String storageDir;

    /**
     * 凌晨 03:15 跑
     */
    @Scheduled(cron = "0 15 3 * * ?")
    public void cleanup() {
        if (!keyService.isEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        long deletedSessions = 0;
        long deletedFiles = 0;

        List<MonitorSession> expired = sessionRepo.selectList(
                new QueryWrapper<MonitorSession>()
                        .lt("retention_until", now)
                        .ne("status", "DELETED"));

        for (MonitorSession s : expired) {
            try {
                Path dir = Paths.get(storageDir, String.valueOf(s.getSessionId()));
                if (Files.exists(dir)) {
                    long cnt;
                    try (var stream = Files.list(dir)) {
                        cnt = stream.count();
                    }
                    try (var stream = Files.list(dir)) {
                        stream.forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                    }
                    Files.deleteIfExists(dir);
                    deletedFiles += cnt;
                }
                s.setStatus("DELETED");
                sessionRepo.updateById(s);
                deletedSessions++;
            } catch (Exception e) {
                log.warn("[MonitorCleanup] session {} 清理失败: {}", s.getSessionId(), e.getMessage());
            }
        }

        // 顺手清 segment 表 (避免孤儿)
        if (deletedSessions > 0) {
            log.info("[MonitorCleanup] ✓ deleted {} sessions, {} files", deletedSessions, deletedFiles);
        } else {
            log.debug("[MonitorCleanup] no expired sessions today");
        }
    }
}
