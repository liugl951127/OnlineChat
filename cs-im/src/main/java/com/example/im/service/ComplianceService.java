package com.example.im.service;

import com.example.im.domain.*;
import com.example.im.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 合规检查服务（金融产品购买必过 4 道关）
 *
 * <ol>
 *   <li>实名认证：检查 customer.phoneVerified=1</li>
 *   <li>风险评估：检查风险评估存在且未过期</li>
 *   <li>适当性匹配：产品风险等级 ≤ 客户风险等级</li>
 *   <li>反洗钱：单笔金额 ≤ 5 万 / 单日累计 ≤ 20 万</li>
 * </ol>
 *
 * <p>任一检查失败 → 整体 REJECTED，记录原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    /** 反洗钱阈值：单笔 / 单日 */
    private static final double AML_SINGLE_LIMIT = 50000.0;
    private static final double AML_DAILY_LIMIT = 200000.0;

    private final ComplianceCheckMapper checkMapper;
    private final FinancialOrderMapper orderMapper;
    private final UserVerifyService userVerifyService; // 调用 cs-auth 验证实名
    private final RiskAssessmentService riskService;

    /**
     * 执行 4 项检查
     *
     * @return ComplianceCheck（已持久化）
     */
    public ComplianceCheck check(String customerId, String orderNo, String productCode,
                                 String productRiskLevel, double amount) {
        ComplianceCheck check = new ComplianceCheck();
        check.setOrderNo(orderNo);
        check.setCreatedAt(LocalDateTime.now());

        List<String> rejects = new ArrayList<>();

        // 1) 实名认证
        if (userVerifyService.isPhoneVerified(customerId)) {
            check.setIdentityCheck(1);
        } else {
            check.setIdentityCheck(0);
            rejects.add("未实名认证");
        }

        // 2) 风险评估
        RiskAssessment ra = riskService.getLatestValid(customerId);
        if (ra == null) {
            check.setRiskCheck(0);
            rejects.add("缺少有效风险评估");
        } else {
            check.setRiskCheck(1);
        }

        // 3) 适当性匹配
        if (ra != null && isMatch(ra.getRiskLevel(), productRiskLevel)) {
            check.setSuitabilityCheck(1);
        } else {
            check.setSuitabilityCheck(0);
            rejects.add("产品风险等级超过您的承受能力");
        }

        // 4) 反洗钱
        double todayTotal = todayTotalAmount(customerId);
        if (amount > AML_SINGLE_LIMIT) {
            check.setAmlCheck(0);
            rejects.add("单笔金额超过 5 万限额");
        } else if (todayTotal + amount > AML_DAILY_LIMIT) {
            check.setAmlCheck(0);
            rejects.add("单日累计金额将超过 20 万限额");
        } else {
            check.setAmlCheck(1);
        }

        // 总体结论
        if (rejects.isEmpty()) {
            check.setOverallResult("PASS");
        } else {
            check.setOverallResult("REJECTED");
            check.setRemark(String.join("; ", rejects));
        }

        checkMapper.insert(check);
        log.info("[Compliance] order={} result={} reasons={}", orderNo, check.getOverallResult(), rejects);
        return check;
    }

    /** 适当性：客户等级 ≥ 产品等级 才匹配
     *  CONSERVATIVE=1 < MODERATE=2 < AGGRESSIVE=3
     */
    private boolean isMatch(String customerLevel, String productLevel) {
        return level(customerLevel) >= level(productLevel);
    }

    private int level(String s) {
        if (s == null) return 0;
        return switch (s) {
            case "CONSERVATIVE" -> 1;
            case "MODERATE" -> 2;
            case "AGGRESSIVE" -> 3;
            default -> 0;
        };
    }

    private double todayTotalAmount(String customerId) {
        // 查当天所有非 REJECTED 订单累计
        List<FinancialOrder> all = orderMapper.findByCustomerIdOrderByIdDesc(customerId);
        double sum = 0;
        for (FinancialOrder o : all) {
            if (o.getCreatedAt() == null) continue;
            if (o.getCreatedAt().toLocalDate().equals(LocalDateTime.now().toLocalDate())
                && !"REJECTED".equals(o.getStatus())) {
                sum += o.getAmount();
            }
        }
        return sum;
    }
}