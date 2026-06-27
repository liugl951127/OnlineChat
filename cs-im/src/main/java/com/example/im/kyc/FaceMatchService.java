package com.example.im.kyc;

import com.example.common.ApiException;
import com.example.im.kyc.tencent.TencentFaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 人脸比对服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>比对身份证照片 vs 活体抓拍照片，返回相似度（0-100）。
 *
 * <p>生产替换：百度人脸识别 API / 阿里云视觉智能 / 腾讯云人脸核身
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceMatchService {

    private static final Random RND = new Random();

    /** 通过阈值 */
    public static final double PASS_THRESHOLD = 80.0;

    private final KycMockProperties mockProps;
    private final TencentFaceClient tencentFaceClient;

    public Map<String, Object> compare(String idCardImgUrl, String faceImgUrl) {
        if (mockProps.isFaceMatch()) {
            return compareMock(idCardImgUrl, faceImgUrl);
        }
        return compareReal(idCardImgUrl, faceImgUrl);
    }

    private Map<String, Object> compareMock(String idCardImgUrl, String faceImgUrl) {
        log.info("[FaceMatch-Mock] 比对 idCard={} face={}", idCardImgUrl, faceImgUrl);

        try { Thread.sleep(200 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        double score = 88 + RND.nextDouble() * 10;  // 88-98
        Map<String, Object> result = new HashMap<>();
        result.put("score", Math.round(score * 100) / 100.0);
        result.put("passed", score >= PASS_THRESHOLD);
        result.put("threshold", PASS_THRESHOLD);
        return result;
    }

    private Map<String, Object> compareReal(String idCardImgUrl, String faceImgUrl) {
        log.info("[FaceMatch-Real] 调腾讯云人脸比对 idCard={} face={}", idCardImgUrl, faceImgUrl);

        // 提取 base64
        String a = extractBase64(idCardImgUrl);
        String b = extractBase64(faceImgUrl);

        // 调腾讯云 CompareFace
        return tencentFaceClient.compareFace(a, b);
    }

    private String extractBase64(String image) {
        if (image == null || image.isBlank()) return image;
        int idx = image.indexOf("base64,");
        if (idx > 0) return image.substring(idx + "base64,".length());
        return image;
    }
}