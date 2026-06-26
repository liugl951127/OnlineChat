package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 人脸比对服务（v2.0.0 Mock 实现）
 *
 * <p>比对身份证照片 vs 活体抓拍照片，返回相似度（0-100）。
 *
 * <p>阈值建议：
 * <ul>
 *   <li>相似度 ≥ 80：自动通过</li>
 *   <li>60-80：人工复核</li>
 *   <li>&lt; 60：拒绝</li>
 * </ul>
 *
 * <p>生产替换：百度人脸识别 API / 阿里云视觉智能 / 腾讯云人脸核身
 */
@Slf4j
@Service
public class FaceMatchService {

    /** 随机数 */
    private static final Random RND = new Random();

    /** 通过阈值 */
    public static final double PASS_THRESHOLD = 80.0;

    /**
     * 人脸比对
     *
     * @param idCardImgUrl  身份证照片 URL
     * @param faceImgUrl    活体照片 URL
     * @return { score: 0-100, passed: bool, detail: "" }
     */
    public Map<String, Object> compare(String idCardImgUrl, String faceImgUrl) {
        log.info("[FaceMatch-Mock] 比对 idCard={} face={}", idCardImgUrl, faceImgUrl);

        // 1) 模拟处理
        try { Thread.sleep(250 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        // 2) 模拟 88-98 分（始终通过）
        double score = 88 + RND.nextDouble() * 10;
        boolean passed = score >= PASS_THRESHOLD;

        Map<String, Object> result = new HashMap<>();
        result.put("score", Math.round(score * 100) / 100.0);
        result.put("passed", passed);
        result.put("detail", passed ? "人脸比对通过" : "相似度过低");

        log.info("[FaceMatch-Mock] 结果: score={}, passed={}", score, passed);
        return result;
    }
}