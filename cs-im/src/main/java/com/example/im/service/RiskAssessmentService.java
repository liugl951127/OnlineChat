package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.RiskAssessment;
import com.example.im.repo.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 风险评估服务（金融产品购买前必过）
 *
 * <p>5 题问卷：年龄 / 收入 / 投资经验 / 风险偏好 / 资产占比
 * <br>分数 0-100，对应 3 个风险等级。
 *
 * <p>评估有效期 1 年，过期需重新评估。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final RiskAssessmentMapper mapper;

    /**
     * 提交问卷 → 计算分数 → 写入数据库
     *
     * @return 评估结果（含 riskLevel + score）
     */
    public Map<String, Object> assess(String customerId, Map<String, Object> answers) {
        if (answers == null || answers.size() < 5) {
            throw new ApiException(400, "问卷不完整（5 题必填）");
        }
        int age = toInt(answers.get("age"));          // 0=18-30  1=31-50  2=51+
        int income = toInt(answers.get("income"));    // 0=<10万  1=10-50万  2=>50万
        int experience = toInt(answers.get("experience")); // 0=无  1=1-3年  2=>3年
        int preference = toInt(answers.get("preference")); // 0=保本  1=稳健  2=激进
        int ratio = toInt(answers.get("ratio"));      // 0=<10%  1=10-30%  2=>30%

        // 加权计算：年龄 10% + 收入 20% + 经验 20% + 偏好 30% + 资产 20%
        int score = (int) (
            age * 10 +
            income * 20 +
            experience * 20 +
            preference * 30 +
            ratio * 20
        );
        score = Math.min(100, Math.max(0, score));

        String riskLevel;
        if (score < 40) riskLevel = "CONSERVATIVE";
        else if (score < 70) riskLevel = "MODERATE";
        else riskLevel = "AGGRESSIVE";

        RiskAssessment ra = new RiskAssessment();
        ra.setCustomerId(customerId);
        ra.setScore(score);
        ra.setRiskLevel(riskLevel);
        ra.setAnswersJson(toJson(answers));
        ra.setExpiresAt(LocalDateTime.now().plusYears(1));
        ra.setCreatedAt(LocalDateTime.now());
        mapper.insert(ra);
        log.info("[RiskAssessment] customer={} score={} level={}", customerId, score, riskLevel);

        Map<String, Object> result = new HashMap<>();
        result.put("id", ra.getId());
        result.put("score", score);
        result.put("riskLevel", riskLevel);
        result.put("expiresAt", ra.getExpiresAt());
        return result;
    }

    /** 查最新评估（未过期） */
    public RiskAssessment getLatestValid(String customerId) {
        RiskAssessment ra = mapper.findLatestByCustomerId(customerId);
        if (ra == null) return null;
        if (ra.getExpiresAt() != null && ra.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.info("[RiskAssessment] customer={} 评估已过期 (expired={})", customerId, ra.getExpiresAt());
            return null;
        }
        return ra;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}