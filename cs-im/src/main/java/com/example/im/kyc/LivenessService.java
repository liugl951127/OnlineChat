package com.example.im.kyc;

import com.example.common.ApiException;
import com.example.im.kyc.tencent.TencentLivenessClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 活体检测服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>真实业务：客户按要求完成 3-4 个动作（张嘴 / 眨眼 / 摇头 / 点头），
 * 后端通过视频帧分析确认是真人而非照片/视频回放。
 *
 * <p>生产替换：腾讯云慧眼 / 阿里云实人认证 / 百度人脸活体。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LivenessService {

    private static final Random RND = new Random();

    /** 标准动作序列 */
    public static final List<String> ACTIONS = Arrays.asList("MOUTH", "BLINK", "SHAKE", "NOD");

    private final KycMockProperties mockProps;
    private final TencentLivenessClient tencentLivenessClient;

    public Map<String, Object> check(List<String> actions) {
        if (mockProps.isLiveness()) {
            return checkMock(actions);
        }
        return checkReal(actions);
    }

    private Map<String, Object> checkMock(List<String> actions) {
        log.info("[Liveness-Mock] 检测动作: {}", actions);

        if (actions == null || actions.size() < 3) {
            return Map.of("passed", false, "score", 0.0, "detail", "动作不足，至少 3 个");
        }
        for (String a : actions) {
            if (!ACTIONS.contains(a)) {
                return Map.of("passed", false, "score", 0.0, "detail", "未知动作: " + a);
            }
        }
        try { Thread.sleep(300 + RND.nextInt(500)); } catch (InterruptedException ignored) {}
        double score = 85 + RND.nextDouble() * 10;
        return Map.of("passed", true, "score", Math.round(score * 100) / 100.0, "detail", "Mock 通过");
    }

    private Map<String, Object> checkReal(List<String> actions) {
        log.info("[Liveness-Real] 腾讯云慧眼活体检测 actions={}", actions);

        // 真实场景：客户先调用 CreateFaceIdToken 拿到 BizToken，前端 SDK 引导客户做动作，
        // 上传视频到 OSS，再调 GetFaceIdResult 拿结果。
        // 这里简化为直接传视频 base64（实际可能需要先上传到腾讯云 COS 拿 URL）

        // 实际生产中 videoUrl 通过参数传入；这里使用 placeholder
        String videoUrl = "";  // TODO: 视频上传后传入 URL
        if (videoUrl.isBlank()) {
            throw new ApiException(400, "视频 URL 未提供：请先调用 VideoRecordService 上传视频，再调用活体检测");
        }

        // 调腾讯云慧眼动作活体（这里传占位 base64，实际用 URL 版接口）
        // TODO: 改用 DetectFaceId + 视频 URL
        return tencentLivenessClient.checkActionLiveness(videoUrl, actions);
    }
}