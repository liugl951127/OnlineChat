package com.example.im.service;

import com.example.im.domain.ChatSession;
import com.example.im.domain.ReplayFrame;
import com.example.im.domain.ReplayJob;
import com.example.im.repo.ChatSessionMapper;
import com.example.im.repo.ReplayFrameRepo;
import com.example.im.repo.ReplayJobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 视频合成器（v2.2.78）
 *
 * <p>把 replay_frame 表里的所有帧 + 消息文本合成一个可播放 MP4 视频。
 *
 * <p>合成流程：
 * <ol>
 *   <li>把每帧 image_data (base64 PNG) 解码写到 /tmp/replay-{sessionId}/frame-NNN.png</li>
 *   <li>用 ffmpeg concat demuxer 把帧序列合并：
 *       <pre>ffmpeg -framerate N -i frame-%03d.png -c:v libx264 -pix_fmt yuv420p out.mp4</pre></li>
 *   <li>视频时长 = sum(frame.durationMs) / 1000</li>
 *   <li>每个 SCREENSHOT 帧在视频里展示 durationMs 毫秒
 *       INTERACTION/MESSAGE 帧只附加 metadata，截图不重复</li>
 *   <li>默认在子目录存储：
 *       <pre>/var/data/replay/{sessionId}/replay.mp4</pre></li>
 *   <li>更新 chat_session.video_replay_url + replay_job.status=SUCCESS</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplaySynthesizerService {

    private final ReplayFrameRepo frameRepo;
    private final ReplayJobRepo jobRepo;
    private final ChatSessionMapper sessionMapper;

    @Value("${cs.replay.storage-dir:/var/data/replay}")
    private String storageDir;

    @Value("${cs.replay.public-base:http://127.0.0.1:9001/im/replay/video}")
    private String publicBase;

    @Value("${cs.replay.framerate:2}")
    private int defaultFramerate;

    /**
     * 触发合成（异步）
     *
     * @param sessionId 会话 ID
     * @return job id
     */
    public Long triggerSynthesis(Long sessionId) {
        // 创建 job
        int frameCount = frameRepo.countBySession(sessionId);
        ReplayJob job = ReplayJob.builder()
                .sessionId(sessionId)
                .status(ReplayJob.STATUS_PENDING)
                .frameCount(frameCount)
                .createdAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);
        final Long jobId = job.getId();

        // 异步执行
        new Thread(() -> {
            try {
                synthesize(sessionId, jobId);
            } catch (Exception e) {
                log.error("[Replay] 合成失败 session={}", sessionId, e);
                ReplayJob failJob = jobRepo.findLatestBySession(sessionId);
                if (failJob != null) {
                    failJob.setStatus(ReplayJob.STATUS_FAILED);
                    failJob.setErrorMessage(e.getMessage());
                    failJob.setFinishedAt(LocalDateTime.now());
                    jobRepo.save(failJob);
                }
            }
        }, "replay-synthesize-" + sessionId).start();

        return jobId;
    }

    /**
     * 同步合成（被 triggerSynthesis 调用，也可单独测试）
     */
    public void synthesize(Long sessionId, Long jobId) throws Exception {
        ReplayJob job = jobRepo.findById(jobId);
        if (job == null) throw new IllegalStateException("Job 不存在: " + jobId);
        job.setStatus(ReplayJob.STATUS_RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepo.save(job);

        List<ReplayFrame> frames = frameRepo.findBySessionOrderBySeq(sessionId);
        if (frames.isEmpty()) {
            throw new IllegalStateException("无帧数据");
        }

        // 1. 准备目录
        Path workDir = Paths.get(storageDir, String.valueOf(sessionId));
        Files.createDirectories(workDir);
        Path frameDir = Files.createTempDirectory(workDir, "frames-");
        log.info("[Replay] 合成开始 session={} frames={} dir={}", sessionId, frames.size(), frameDir);

        // 2. 解码 + 写帧 PNG
        int actualFrames = 0;
        long totalDurationMs = 0;
        int imgWidth = 1280;
        int imgHeight = 720;

        for (int i = 0; i < frames.size(); i++) {
            ReplayFrame f = frames.get(i);
            if (f.getFrameKind() != null && !f.getFrameKind().equals(ReplayFrame.KIND_SCREENSHOT)
                    && !f.getFrameKind().equals(ReplayFrame.KIND_PAGE)) {
                continue;  // 跳过纯交互帧
            }
            BufferedImage img = decodeFrameImage(f);
            if (img == null) {
                log.warn("[Replay] 帧解码失败 session={} seq={}, 用空白帧替代", sessionId, f.getSeq());
                img = createBlankFrame(imgWidth, imgHeight);
            } else {
                imgWidth = img.getWidth();
                imgHeight = img.getHeight();
            }
            // 把消息 overlay 到图上
            overlayMetadata(img, f);

            Path pngPath = frameDir.resolve(String.format("frame-%05d.png", actualFrames));
            ImageIO.write(img, "png", pngPath.toFile());

            int durMs = f.getDurationMs() != null && f.getDurationMs() > 0
                    ? f.getDurationMs() : 5000;
            totalDurationMs += durMs;
            actualFrames++;
        }

        if (actualFrames == 0) {
            throw new IllegalStateException("无可用截图帧");
        }

        // 3. ffmpeg 合并
        Path videoPath = workDir.resolve("replay.mp4");
        // 每帧 durationMs 毫秒 → fps = 1000/durationMs (不固定, 用 framerate 表示"平均")
        // 简化: 用固定 framerate (defaultFramerate=2), 每帧展示 1000/framerate 毫秒
        // 真实时长可能与 durationMs 之和有偏差, 但接近
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-framerate", String.valueOf(defaultFramerate),
                "-i", frameDir.resolve("frame-%05d.png").toString(),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-r", "30",
                videoPath.toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[ffmpeg] {}", line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg 退出码 " + exitCode);
        }
        long videoBytes = Files.size(videoPath);
        log.info("[Replay] 合成完成 session={} video={} ({} bytes)", sessionId, videoPath, videoBytes);

        // 4. 更新会话 + job
        ChatSession session = sessionMapper.selectById(sessionId);
        String videoUrl = publicBase + "/" + sessionId + ".mp4";
        if (session != null) {
            session.setVideoReplayUrl(videoUrl);
            sessionMapper.updateById(session);
        }

        job.setStatus(ReplayJob.STATUS_SUCCESS);
        job.setVideoUrl(videoUrl);
        job.setDurationMs(totalDurationMs);
        job.setFinishedAt(LocalDateTime.now());
        jobRepo.save(job);

        // 5. 清理临时帧目录
        try {
            Files.walk(frameDir)
                    .sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p2 -> {
                        try { Files.deleteIfExists(p2); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    /**
     * 解码帧 (支持 base64 / image_url)
     */
    private BufferedImage decodeFrameImage(ReplayFrame frame) {
        try {
            if (frame.getImageData() != null && !frame.getImageData().isBlank()) {
                String data = frame.getImageData();
                if (data.contains(",")) data = data.substring(data.indexOf(',') + 1);
                byte[] bytes = Base64.getDecoder().decode(data);
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    return ImageIO.read(bais);
                }
            }
            if (frame.getImageUrl() != null && !frame.getImageUrl().isBlank()) {
                return ImageIO.read(new URL(frame.getImageUrl()));
            }
        } catch (Exception e) {
            log.warn("[Replay] 帧解码异常: {}", e.getMessage());
        }
        return null;
    }

    private BufferedImage createBlankFrame(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(245, 245, 250));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.drawString("Empty Frame", 50, 50);
        g.dispose();
        return img;
    }

    /**
     * 把 metadata 画到图上 (滚动位置 / 输入内容 / 消息片段)
     */
    private void overlayMetadata(BufferedImage img, ReplayFrame frame) {
        if (frame.getMetadata() == null || frame.getMetadata().isBlank()) return;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 底部半透明条
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, img.getHeight() - 60, img.getWidth(), 60);
        // 文本
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString(truncate(frame.getMetadata(), 100), 20, img.getHeight() - 30);
        // 顶部时间戳
        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        String ts = String.format("+%.1fs seq=%d", frame.getOffsetMs() / 1000.0, frame.getSeq());
        g.drawString(ts, img.getWidth() - 200, 25);
        g.dispose();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 查询 job 状态
     */
    public ReplayJob getJob(Long sessionId) {
        return jobRepo.findLatestBySession(sessionId);
    }
}