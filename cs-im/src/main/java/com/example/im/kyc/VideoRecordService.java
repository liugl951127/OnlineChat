package com.example.im.kyc;

import com.example.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 视频双录服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>真实生产：阿里云 OSS / 腾讯云 COS + ffmpeg 抽帧 + OpenCV 二次确认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoRecordService {

    private static final Random RND = new Random();

    private final KycMockProperties mockProps;

    public Map<String, Object> save(Long applicationId, Long statementId, int segmentNo,
                                     String videoBase64, int durationSec) {
        if (mockProps.isVideoRecord()) {
            return saveMock(applicationId, statementId, segmentNo, videoBase64, durationSec);
        }
        return saveReal(applicationId, statementId, segmentNo, videoBase64, durationSec);
    }

    private Map<String, Object> saveMock(Long applicationId, Long statementId, int segmentNo,
                                          String videoBase64, int durationSec) {
        log.info("[Video-Mock] 保存视频 applicationId={} segment={} duration={}s",
                applicationId, segmentNo, durationSec);

        String mockUrl = "https://mock-oss.example.com/kyc-video/" + applicationId + "/" + segmentNo + ".webm";

        Map<String, Object> result = new HashMap<>();
        result.put("videoUrl", mockUrl);
        result.put("durationSec", durationSec);
        result.put("fileSizeKb", videoBase64 == null ? 0 : videoBase64.length() / 1024);
        result.put("checksum", checksum(videoBase64));
        return result;
    }

    private Map<String, Object> saveReal(Long applicationId, Long statementId, int segmentNo,
                                          String videoBase64, int durationSec) {
        log.info("[Video-Real] 上传 OSS applicationId={} segment={}", applicationId, segmentNo);

        // 生产实现：
        // OSSClient oss = new OSSClient(endpoint, accessKey, secret);
        // String key = "kyc-video/" + applicationId + "/" + segmentNo + ".webm";
        // oss.putObject(bucket, key, new ByteArrayInputStream(Base64.decode(videoBase64)));
        // String url = "https://" + bucket + "." + endpoint + "/" + key;

        throw new ApiException(501, "视频上传 OSS 未配置：请实现 VideoRecordService.saveReal() 或将 kyc.mock.video-record 改为 true");
    }

    private String checksum(String data) {
        if (data == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "0".repeat(64);
        }
    }
}