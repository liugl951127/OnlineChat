package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.ChatSession;
import com.example.im.domain.ReplayFrame;
import com.example.im.domain.ReplayJob;
import com.example.im.repo.ChatSessionMapper;
import com.example.im.service.ReplayFrameService;
import com.example.im.service.ReplaySynthesizerService;
import com.example.im.service.VideoReplayService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频回溯 REST 接口（v1.9.0 + v2.2.78 扩展）
 *
 * <p>完整流程：
 * <ol>
 *   <li>客户端进入 ChatRoom → 定时截图 → POST /im/replay/capture</li>
 *   <li>会话结束 → POST /im/replay/{id}/finish → 触发 ffmpeg 合成</li>
 *   <li>轮询 GET /im/replay/{id}/job 查看合成状态</li>
 *   <li>前端播放器 GET /im/replay/{id} → 拿 videoUrl + frames 时间轴</li>
 *   <li>视频文件 GET /im/replay/video/{file} → 流式下载 MP4</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/im/replay")
@RequiredArgsConstructor
public class VideoReplayController {

    private final VideoReplayService replayService;
    private final ReplayFrameService frameService;
    private final ReplaySynthesizerService synthesizer;
    private final ChatSessionMapper sessionMapper;

    @Value("${cs.replay.storage-dir:/var/data/replay}")
    private String storageDir;

    /** 客户端上传一帧 */
    @PostMapping("/capture")
    public ApiResponse<Map<String, Object>> capture(@RequestBody CaptureFrameReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        if (req.getSessionId() == null) throw new ApiException(400, "sessionId 必填");

        ChatSession session = sessionMapper.selectById(req.getSessionId());
        if (session == null) throw new ApiException(404, "会话不存在");

        String uploadedBy = ctx.getUserId() != null ? ctx.getUserId() : "customer";

        ReplayFrame f = frameService.capture(
                req.getSessionId(),
                req.getMessageId(),
                req.getFrameKind() != null ? req.getFrameKind() : ReplayFrame.KIND_SCREENSHOT,
                req.getImageData(),
                req.getWidth(),
                req.getHeight(),
                req.getMetadata(),
                uploadedBy,
                session.getCreatedAt() != null ? session.getCreatedAt() : LocalDateTime.now()
        );
        Map<String, Object> r = new HashMap<>();
        r.put("id", f.getId());
        r.put("seq", f.getSeq());
        r.put("offsetMs", f.getOffsetMs());
        r.put("frameCount", frameService.frameCount(req.getSessionId()));
        return ApiResponse.ok(r);
    }

    /** 批量上传帧 (前端一次发多帧减少网络开销) */
    @PostMapping("/capture-batch")
    public ApiResponse<Map<String, Object>> captureBatch(@RequestBody BatchCaptureReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        if (req.getSessionId() == null) throw new ApiException(400, "sessionId 必填");
        if (req.getFrames() == null || req.getFrames().isEmpty()) {
            throw new ApiException(400, "frames 不能为空");
        }
        ChatSession session = sessionMapper.selectById(req.getSessionId());
        if (session == null) throw new ApiException(404, "会话不存在");

        String uploadedBy = ctx.getUserId() != null ? ctx.getUserId() : "customer";
        List<ReplayFrame> frames = new ArrayList<>();
        for (CaptureFrameReq one : req.getFrames()) {
            frames.add(ReplayFrame.builder()
                    .sessionId(req.getSessionId())
                    .messageId(one.getMessageId())
                    .capturedAt(one.getCapturedAt() != null ? one.getCapturedAt() : LocalDateTime.now())
                    .offsetMs(one.getOffsetMs())
                    .frameKind(one.getFrameKind() != null ? one.getFrameKind() : ReplayFrame.KIND_SCREENSHOT)
                    .imageData(one.getImageData())
                    .width(one.getWidth())
                    .height(one.getHeight())
                    .durationMs(one.getDurationMs() != null ? one.getDurationMs() : 5000)
                    .metadata(one.getMetadata())
                    .uploadedBy(uploadedBy)
                    .build());
        }
        frameService.captureBatch(frames, session.getCreatedAt());
        Map<String, Object> r = new HashMap<>();
        r.put("saved", frames.size());
        r.put("totalFrames", frameService.frameCount(req.getSessionId()));
        return ApiResponse.ok(r);
    }

    /** 会话结束触发合成 */
    @PostMapping("/{sessionId}/finish")
    public ApiResponse<Map<String, Object>> finish(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new ApiException(404, "会话不存在");

        Long jobId = synthesizer.triggerSynthesis(sessionId);
        Map<String, Object> r = new HashMap<>();
        r.put("jobId", jobId);
        r.put("frameCount", frameService.frameCount(sessionId));
        r.put("status", "PENDING");
        r.put("message", "合成任务已提交, 轮询 /im/replay/" + sessionId + "/job 查看进度");
        return ApiResponse.ok(r);
    }

    /** 查 job 状态 */
    @GetMapping("/{sessionId}/job")
    public ApiResponse<ReplayJob> job(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        ReplayJob job = synthesizer.getJob(sessionId);
        return ApiResponse.ok(job);
    }

    /** 列出所有帧 */
    @GetMapping("/{sessionId}/frames")
    public ApiResponse<Map<String, Object>> frames(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        List<ReplayFrame> frames = frameService.findFrames(sessionId);
        Map<String, Object> r = new HashMap<>();
        r.put("sessionId", sessionId);
        r.put("frameCount", frames.size());
        r.put("frames", frames);
        return ApiResponse.ok(r);
    }

    /** 生成回放数据 (兼容 v1.9.0) */
    @GetMapping("/{sessionId}")
    public ApiResponse<Map<String, Object>> replay(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(replayService.generateReplay(sessionId));
    }

    /** 设置回放视频 URL（管理员） */
    @PostMapping("/{sessionId}/video-url")
    public ApiResponse<Boolean> setVideoUrl(@PathVariable Long sessionId, @RequestBody SetUrlReq req) {
        replayService.setReplayUrl(sessionId, req.getVideoUrl());
        return ApiResponse.ok(true);
    }

    /** 给消息打帧截图 URL */
    @PostMapping("/message/{messageId}/frame-url")
    public ApiResponse<Boolean> setFrameUrl(@PathVariable Long messageId, @RequestBody SetUrlReq req) {
        replayService.setFrameUrl(messageId, req.getVideoUrl());
        return ApiResponse.ok(true);
    }

    /** 视频文件下载 (MP4 stream) */
    @GetMapping("/video/{sessionId}.mp4")
    public ResponseEntity<Resource> videoFile(@PathVariable Long sessionId) throws java.io.IOException {
        Path videoPath = Paths.get(storageDir, String.valueOf(sessionId), "replay.mp4");
        if (!Files.exists(videoPath)) {
            throw new ApiException(404, "回放视频不存在: " + videoPath);
        }
        Resource resource = new FileSystemResource(videoPath);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"replay-" + sessionId + ".mp4\"")
                .contentLength(resource.contentLength())
                .body(resource);
    }

    /** 视频元数据 */
    @GetMapping("/{sessionId}/meta")
    public ApiResponse<Map<String, Object>> meta(@PathVariable Long sessionId) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new ApiException(404, "会话不存在");

        Map<String, Object> r = new HashMap<>();
        r.put("sessionId", sessionId);
        r.put("videoUrl", session.getVideoReplayUrl());
        r.put("frameCount", frameService.frameCount(sessionId));
        r.put("startTime", session.getCreatedAt());
        r.put("endTime", session.getEndedAt());
        ReplayJob job = synthesizer.getJob(sessionId);
        if (job != null) {
            r.put("jobStatus", job.getStatus());
            r.put("jobDurationMs", job.getDurationMs());
        }
        return ApiResponse.ok(r);
    }

    @Data
    public static class SetUrlReq {
        private String videoUrl;
    }

    @Data
    public static class CaptureFrameReq {
        private Long sessionId;
        private Long messageId;
        private String frameKind;        // SCREENSHOT/PAGE/INTERACTION/MESSAGE
        private String imageData;        // base64 data:image/png;base64,...
        private Integer width;
        private Integer height;
        private Integer durationMs;
        private LocalDateTime capturedAt;
        private Long offsetMs;
        private String metadata;
    }

    @Data
    public static class BatchCaptureReq {
        private Long sessionId;
        private List<CaptureFrameReq> frames;
    }
}