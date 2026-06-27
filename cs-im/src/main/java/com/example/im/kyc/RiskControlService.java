package com.example.im.kyc;

import com.example.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 风控服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>生产：百融金服 / 同盾科技 / 银联反欺诈 / Drools 规则引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlService {

    private static final Random RND = new Random();

    private final KycMockProperties mockProps;

    public Map<String, Object> assess(String idCardNo, String deviceId, String customerId) {
        if (mockProps.isRiskControl()) {
            return assessMock(idCardNo, deviceId, customerId);
        }
        return assessReal(idCardNo, deviceId, customerId);
    }

    private Map<String, Object> assessMock(String idCardNo, String deviceId, String customerId) {
        log.info("[RiskControl-Mock] 评估 idCard={} device={} customer={}",
                idCardNo != null ? idCardNo.substring(0, 6) + "****" : "null",
                deviceId, customerId);

        boolean blacklistHit = idCardNo != null && idCardNo.contains("9999");
        String riskLevel;
        if (blacklistHit) {
            riskLevel = "HIGH";
        } else {
            int roll = RND.nextInt(100);
            riskLevel = roll < 70 ? "LOW" : (roll < 90 ? "MID" : "HIGH");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("riskLevel", riskLevel);
        result.put("blacklistHit", blacklistHit);
        result.put("reason", blacklistHit ? "命中黑名单（身份证含 9999）" : "Mock 评估");
        return result;
    }

    private Map<String, Object> assessReal(String idCardNo, String deviceId, String customerId) {
        log.info("[RiskControl-Real] 调用百融/同盾风控 customer={}", customerId);

        // 生产实现：
        // BairongClient client = new BairongClient(apiKey);
        // RiskAssessRequest req = new RiskAssessRequest(idCardNo, deviceId, customerId);
        // RiskAssessResponse resp = client.assess(req);
        // return Map.of(
        //     "riskLevel", resp.getLevel(),  // LOW / MID / HIGH
        //     "blacklistHit", resp.isBlacklistHit(),
        //     "reason", resp.getReason()
        // );

        throw new ApiException(501, "风控真实 API 未配置：请实现 RiskControlService.assessReal() 或将 kyc.mock.risk-control 改为 true");
    }
}