package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 视频双录服务（v2.0.0 Mock 实现）
 *
 * <p>真实业务场景：
 * <ol>
 *   <li>前端调用 MediaRecorder API 录制 webm 视频</li>
 *   <li>上传到 OSS / S3 / 本地存储</li>
 *   <li>返回 videoUrl + 校验和 + 时长</li>
 *   <li>后台审核员人工抽检（合规要求）</li>
 * </ol>
 *
 * <p>真实生产应：
 * <ul>
 *   <li>使用阿里云 OSS / 腾讯云 COS / AWS S3</li>
 *   <li>使用 ffmpeg 抽帧 + 字幕识别 + 关键帧审核</li>
 *   <li>使用 OpenCV / FaceNet 二次确认是否本人</li>
 * </ul>
 */
@Slf4j
@Service
public class VideoRecordService {

    private static final Random RND = new Random();

    /**
     * 保存视频（Mock：返回占位 URL）
     *
     * @param applicationId  申请单 ID
     * @param statementId    声明 ID
     * @param segmentNo      段序号
     * @param videoBase64    视频 Base64（生产应上传到 OSS 拿 URL）
     * @param durationSec    时长（秒）
     * @return { videoUrl, durationSec, fileSizeKb, checksum }
     */
    public Map<String, Object> save(Long applicationId, Long statementId, int segmentNo,
                                     String videoBase64, int durationSec) {
        log.info("[Video-Mock] 保存视频 applicationId={} segment={} duration={}s",
                applicationId, segmentNo, durationSec);

        // 1) Mock：返回占位 URL（生产：上传 OSS）
        String fakeUrl = String.format("https://oss.example.com/kyc-video/%d-%d.webm",
                applicationId, segmentNo);

        // 2) Mock 文件大小
        int fileSizeKb = (int) (videoBase64 != null ? videoBase64.length() / 1024 : 100 + RND.nextInt(500));

        // 3) Mock 校验和（SHA-256 of base64 prefix）
        String checksum = sha256(videoBase64 != null ? videoBase64.substring(0, Math.min(100, videoBase64.length())) : "");

        Map<String, Object> result = new HashMap<>();
        result.put("videoUrl", fakeUrl);
        result.put("durationSec", durationSec);
        result.put("fileSizeKb", fileSizeKb);
        result.put("checksum", checksum);

        log.info("[Video-Mock] 保存成功 url={} size={}KB", fakeUrl, fileSizeKb);
        return result;
    }

    /** SHA-256（简化） */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().substring(0, 32);  // 取前 32 字符
        } catch (Exception e) {
            return "0".repeat(32);
        }
    }
}