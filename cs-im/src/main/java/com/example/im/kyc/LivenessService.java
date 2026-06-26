package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 活体检测服务（v2.0.0 Mock 实现）
 *
 * <p>真实业务：客户按要求完成 3-4 个动作（张嘴 / 眨眼 / 摇头 / 点头），
 * 后端通过视频帧分析确认是真人而非照片/视频回放。
 *
 * <p>生产替换：
 * <ul>
 *   <li>腾讯云慧眼 - 人脸核身</li>
 *   <li>阿里云实人认证</li>
 *   <li>百度人脸活体</li>
 * </ul>
 */
@Slf4j
@Service
public class LivenessService {

    /** 随机数 */
    private static final Random RND = new Random();

    /** 标准动作序列（任选 3-4 个） */
    public static final List<String> ACTIONS = Arrays.asList("MOUTH", "BLINK", "SHAKE", "NOD");

    /**
     * 检测活体动作
     *
     * @param actions 客户完成的动作序列（如 ["MOUTH","BLINK","SHAKE"]）
     * @return { passed: bool, score: 0-100, detail: "" }
     */
    public Map<String, Object> check(List<String> actions) {
        log.info("[Liveness-Mock] 检测动作序列: {}", actions);

        // 1) 校验：至少完成 3 个动作
        if (actions == null || actions.size() < 3) {
            return Map.of("passed", false, "score", 0.0, "detail", "动作不足，至少 3 个");
        }

        // 2) 校验：必须是标准动作
        for (String a : actions) {
            if (!ACTIONS.contains(a)) {
                return Map.of("passed", false, "score", 0.0, "detail", "非法动作: " + a);
            }
        }

        // 3) 模拟处理
        try { Thread.sleep(300 + RND.nextInt(400)); } catch (InterruptedException ignored) {}

        // 4) 100% 通过（生产可调整）
        boolean passed = true;
        double score = 90 + RND.nextDouble() * 10;

        Map<String, Object> result = new HashMap<>();
        result.put("passed", passed);
        result.put("score", score);
        result.put("detail", passed ? "活体检测通过" : "活体检测未通过");

        log.info("[Liveness-Mock] 结果: passed={}, score={}", passed, score);
        return result;
    }
}