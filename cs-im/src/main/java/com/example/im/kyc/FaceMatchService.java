package com.example.im.kyc;

import com.example.im.kyc.local.LocalFaceMatchService;
import com.example.im.kyc.tencent.TencentFaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 人脸比对服务（v2.2.1 三模式）
 *
 * <p>三种实现：
 * <ol>
 *   <li><b>local</b> — 本地 OpenCV Haar + LBP（离线，零成本）</li>
 *   <li><b>tencent</b> — 腾讯云 CompareFace（高精度，深度学习）</li>
 *   <li><b>mock</b> — 固定数据（开发测试）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceMatchService {

    public static final double PASS_THRESHOLD = 80.0;

    private final KycMockProperties mockProps;
    private final LocalFaceMatchService localFaceService;
    private final TencentFaceClient tencentFaceClient;

    public Map<String, Object> compare(String idCardImg, String faceImg) {
        if (mockProps.isFaceMatch()) {
            return compareMock(idCardImg, faceImg);
        }
        // 优先本地
        if (localFaceService != null) {
            try {
                log.info("[FaceMatch] 尝试本地自研");
                return localFaceService.compare(idCardImg, faceImg);
            } catch (Exception e) {
                log.warn("[FaceMatch] 本地失败，fallback 到腾讯云: {}", e.getMessage());
            }
        }
        log.info("[FaceMatch] 使用腾讯云 CompareFace");
        return compareTencent(idCardImg, faceImg);
    }

    private Map<String, Object> compareMock(String idCardImgUrl, String faceImgUrl) {
        double score = 88 + Math.random() * 10;
        Map<String, Object> result = new HashMap<>();
        result.put("score", Math.round(score * 100) / 100.0);
        result.put("passed", score >= PASS_THRESHOLD);
        result.put("threshold", PASS_THRESHOLD);
        result.put("method", "Mock");
        return result;
    }

    private Map<String, Object> compareTencent(String idCardImg, String faceImg) {
        String a = extractBase64(idCardImg);
        String b = extractBase64(faceImg);
        return tencentFaceClient.compareFace(a, b);
    }

    private String extractBase64(String image) {
        if (image == null || image.isBlank()) return image;
        int idx = image.indexOf("base64,");
        return idx > 0 ? image.substring(idx + "base64,".length()) : image;
    }
}