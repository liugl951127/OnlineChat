package com.example.im.kyc;

import com.example.common.ApiException;
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
        log.info("[Liveness-Real] 调用真实活体 API actions={}", actions);

        // 生产实现：腾讯云慧眼 DetectFace + VideoFaceFusion
        // HivisionClient client = factory.hivision();
        // DetectFaceResult result = client.detectFace(videoBytes, actions);
        //
        // return Map.of(
        //     "passed", result.isLive(),
        //     "score", result.getScore(),
        //     "detail", result.getMessage()
        // );

        throw new ApiException(501, "活体检测真实 API 未配置：请实现 LivenessService.checkReal() 或将 kyc.mock.liveness 改为 true");
    }
}