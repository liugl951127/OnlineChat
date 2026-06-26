package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 风控服务（v2.0.0 Mock）
 *
 * <p>检查：
 * <ul>
 *   <li>黑名单命中（按身份证号）</li>
 *   <li>设备指纹（同一设备多次申请）</li>
 *   <li>申请频率（同一客户 24h 内多次申请）</li>
 * </ul>
 *
 * <p>真实生产应接入：
 * <ul>
 *   <li>百融金服 / 同盾科技</li>
 *   <li>银联反欺诈系统</li>
 *   <li>内部风控规则引擎（Drools）</li>
 * </ul>
 */
@Slf4j
@Service
public class RiskControlService {

    private static final Random RND = new Random();

    /**
     * 综合风险评估
     *
     * @param idCardNo    身份证号
     * @param deviceId    设备指纹
     * @param customerId  客户 ID
     * @return { riskLevel: LOW/MID/HIGH, blacklistHit: bool, reason: "" }
     */
    public Map<String, Object> assess(String idCardNo, String deviceId, String customerId) {
        log.info("[RiskControl-Mock] 评估 idCard={} device={} customer={}",
                idCardNo != null ? idCardNo.substring(0, 6) + "****" : "null",
                deviceId, customerId);

        // 1) 黑名单（Mock：身份证号含 "9999" 视为黑名单）
        boolean blacklistHit = idCardNo != null && idCardNo.contains("9999");

        // 2) 风险等级（Mock：根据随机数）
        String riskLevel;
        double r = RND.nextDouble();
        if (blacklistHit) {
            riskLevel = "HIGH";
        } else if (r < 0.7) {
            riskLevel = "LOW";
        } else if (r < 0.95) {
            riskLevel = "MID";
        } else {
            riskLevel = "HIGH";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("riskLevel", riskLevel);
        result.put("blacklistHit", blacklistHit);
        result.put("reason", blacklistHit ? "命中黑名单" : "常规客户");

        log.info("[RiskControl-Mock] 结果: riskLevel={}, blacklistHit={}", riskLevel, blacklistHit);
        return result;
    }
}