package com.example.im.service;

import com.example.im.domain.ReplayFrame;
import com.example.im.repo.ReplayFrameRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话帧服务（v2.2.78）
 *
 * <p>客户端定时（默认 5 秒）调用 {@link #capture} 上传一帧 + 关联时间戳。
 * 服务端存到 replay_frame 表，会话结束后由 {@link ReplaySynthesizerService}
 * 合并成 MP4。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayFrameService {

    private final ReplayFrameRepo frameRepo;

    /**
     * 客户端上传一帧
     *
     * @param sessionId 会话 ID
     * @param messageId 关联消息 ID (可选, 纯截图帧为 null)
     * @param frameKind SCREENSHOT/PAGE/INTERACTION/MESSAGE
     * @param imageData base64 图片数据 (data:image/png;base64,XXX)
     * @param width 宽
     * @param height 高
     * @param metadata 客户端元数据 (滚动位置/输入框内容/点击元素等 JSON)
     * @param uploadedBy customer/agent
     * @param sessionStart 会话起始时间 (用于算 offsetMs)
     * @return 持久化后的帧（含 seq）
     */
    public ReplayFrame capture(Long sessionId,
                               Long messageId,
                               String frameKind,
                               String imageData,
                               Integer width,
                               Integer height,
                               String metadata,
                               String uploadedBy,
                               LocalDateTime sessionStart) {
        // 计算 offsetMs
        long offsetMs = 0;
        if (sessionStart != null) {
            offsetMs = Duration.between(sessionStart, LocalDateTime.now()).toMillis();
        }
        if (offsetMs < 0) offsetMs = 0;

        // 计算 seq
        int seq = frameRepo.nextSeq(sessionId);

        ReplayFrame frame = ReplayFrame.builder()
                .sessionId(sessionId)
                .messageId(messageId)
                .seq(seq)
                .capturedAt(LocalDateTime.now())
                .offsetMs(offsetMs)
                .frameKind(frameKind != null ? frameKind : ReplayFrame.KIND_SCREENSHOT)
                .imageData(imageData)
                .width(width)
                .height(height)
                .durationMs(5000)  // 默认 5 秒一帧
                .metadata(metadata)
                .uploadedBy(uploadedBy)
                .createdAt(LocalDateTime.now())
                .build();
        frame = frameRepo.save(frame);
        log.debug("[Replay] 帧捕获 session={} seq={} offset={}ms kind={} uploadedBy={}",
                sessionId, seq, offsetMs, frame.getFrameKind(), uploadedBy);
        return frame;
    }

    /** 批量查帧（按 seq 升序） */
    public List<ReplayFrame> findFrames(Long sessionId) {
        return frameRepo.findBySessionOrderBySeq(sessionId);
    }

    /** 帧总数 */
    public int frameCount(Long sessionId) {
        return frameRepo.countBySession(sessionId);
    }

    /**
     * 批量上传（一次请求多帧，减少网络开销）
     */
    public List<ReplayFrame> captureBatch(List<ReplayFrame> frames,
                                          LocalDateTime sessionStart) {
        LocalDateTime now = LocalDateTime.now();
        for (ReplayFrame f : frames) {
            int seq = frameRepo.nextSeq(f.getSessionId());
            f.setSeq(seq);
            f.setCreatedAt(now);
            if (sessionStart != null && f.getOffsetMs() == null) {
                f.setOffsetMs(Math.max(0,
                    Duration.between(sessionStart, f.getCapturedAt() != null
                        ? f.getCapturedAt() : now).toMillis()));
            }
            frameRepo.save(f);
        }
        return frames;
    }
}