package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.RichMessage;
import com.example.im.domain.*;
import com.example.im.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 金融产品订单服务（生产级交易流程）
 *
 * <p>完整 5 步状态机：
 * <pre>
 *   1. createOrder        DRAFT
 *   2. riskAssessment     → RISK_ASSESSED
 *   3. complianceCheck    → COMPLIANCE_PASSED / REJECTED
 *   4. pay                → PAYING → SETTLED（生成 Holding）
 *   5. redeem             → REDEEMED（赎回持仓）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialOrderService {

    private final FinancialOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final HoldingMapper holdingMapper;
    private final RiskAssessmentService riskService;
    private final ComplianceService complianceService;
    private final ContractService contractService;

    // ============= 1) 创建订单（草稿） =============

    /**
     * 创建订单（客户 / 坐席均可调用）
     *
     * @param initiatorRole AGENT 协助下单 / CUSTOMER 自助
     */
    @Transactional
    public FinancialOrder createOrder(String customerId, String productCode, double amount, String initiatorRole, String agentUsername) {
        Product p = productMapper.findByCode(productCode);
        if (p == null) throw new ApiException(404, "产品不存在: " + productCode);
        if (!"ON_SALE".equals(p.getStatus())) throw new ApiException(400, "产品不在售");
        if (amount < p.getMinAmount()) throw new ApiException(400, "金额低于起购额 " + p.getMinAmount());
        if (p.getMaxAmount() > 0 && amount > p.getMaxAmount()) throw new ApiException(400, "金额超过限购 " + p.getMaxAmount());

        FinancialOrder o = new FinancialOrder();
        o.setOrderNo("FO" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        o.setCustomerId(customerId);
        if ("AGENT".equals(initiatorRole)) o.setAgentUsername(agentUsername);
        o.setProductCode(productCode);
        o.setProductName(p.getName());
        o.setAmount(amount);
        o.setStatus("DRAFT");
        o.setInitiatorRole(initiatorRole);
        o.setPaymentMethod("MOCK_BANK");
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(o);
        log.info("[Order] created orderNo={} customer={} product={} amount={}",
            o.getOrderNo(), customerId, productCode, amount);
        return o;
    }

    // ============= 2) 风险评估 =============

    @Transactional
    public FinancialOrder assessRisk(String orderNo) {
        FinancialOrder o = mustGet(orderNo);
        if (!"DRAFT".equals(o.getStatus())) {
            throw new ApiException(400, "订单状态不允许评估: " + o.getStatus());
        }
        RiskAssessment ra = riskService.getLatestValid(o.getCustomerId());
        if (ra == null) {
            throw new ApiException(400, "请先完成风险评估问卷");
        }
        o.setRiskLevel(ra.getRiskLevel());
        o.setRiskScore(ra.getScore());
        o.setStatus("RISK_ASSESSED");
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        log.info("[Order] {} 风险评估通过 level={} score={}", orderNo, ra.getRiskLevel(), ra.getScore());
        return o;
    }

    // ============= 3) 合规检查 =============

    @Transactional
    public FinancialOrder runCompliance(String orderNo) {
        FinancialOrder o = mustGet(orderNo);
        if (!"RISK_ASSESSED".equals(o.getStatus())) {
            throw new ApiException(400, "请先完成风险评估");
        }
        Product p = productMapper.findByCode(o.getProductCode());

        ComplianceCheck check = complianceService.check(
            o.getCustomerId(), orderNo, o.getProductCode(), p.getRiskLevel(), o.getAmount());

        o.setComplianceResult(check.getOverallResult());
        o.setComplianceRemark(check.getRemark());

        if ("PASS".equals(check.getOverallResult())) {
            o.setStatus("COMPLIANCE_PASSED");
        } else {
            o.setStatus("REJECTED");
        }
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        log.info("[Order] {} 合规检查 result={}", orderNo, check.getOverallResult());
        return o;
    }

    // ============= 2.5) 生成合同（合规检查通过后调用） =============

    /**
     * 生成合同（调用 ContractService）
     * 状态不跳转（保留 COMPLIANCE_PASSED），签约后跳到 CONTRACT_SIGNED
     */
    public com.example.im.domain.Contract generateContract(String orderNo) {
        FinancialOrder o = mustGet(orderNo);
        if (!"COMPLIANCE_PASSED".equals(o.getStatus())) {
            throw new ApiException(400, "订单未通过合规检查: " + o.getStatus());
        }
        return contractService.generateContract(orderNo, "TPL-FIN-001");
    }

    /**
     * 客户签约后调用 → 状态跳到 CONTRACT_SIGNED
     */
    @Transactional
    public FinancialOrder markContractSigned(String orderNo) {
        FinancialOrder o = mustGet(orderNo);
        if (!"COMPLIANCE_PASSED".equals(o.getStatus())) {
            throw new ApiException(400, "订单未通过合规检查: " + o.getStatus());
        }
        o.setStatus("CONTRACT_SIGNED");
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        log.info("[Order] {} 合同已签约 → CONTRACT_SIGNED", orderNo);
        return o;
    }

    // ============= 4) 支付 =============

    /**
     * 模拟支付：实际生产对接银行/三方支付
     */
    @Transactional
    public FinancialOrder pay(String orderNo, String paymentMethod) {
        FinancialOrder o = mustGet(orderNo);
        if (!"COMPLIANCE_PASSED".equals(o.getStatus())) {
            throw new ApiException(400, "订单未通过合规检查: " + o.getStatus());
        }
        // v2.2.43 必须先签约
        if (!"CONTRACT_SIGNED".equals(o.getStatus())) {
            throw new ApiException(400, "订单未签约: " + o.getStatus() + "，请先调用 /contract/generate 和 /contract/sign");
        }
        o.setStatus("PAYING");
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);

        // 模拟支付成功
        o.setStatus("SETTLED");
        o.setPaidAt(LocalDateTime.now());
        o.setCompletedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);

        // 生成持仓
        Holding h = new Holding();
        h.setCustomerId(o.getCustomerId());
        h.setProductCode(o.getProductCode());
        h.setPrincipal(o.getAmount());
        h.setAccumulatedIncome(0.0);
        h.setStatus("HOLDING");
        h.setStartDate(LocalDateTime.now());
        h.setEndDate(null);
        h.setCreatedAt(LocalDateTime.now());
        h.setUpdatedAt(LocalDateTime.now());
        holdingMapper.insert(h);

        log.info("[Order] {} 支付成功，订单已 SETTLED，生成持仓 id={}", orderNo, h.getId());
        return o;
    }

    // ============= 5) 赎回 =============

    @Transactional
    public Holding redeem(String orderNo) {
        FinancialOrder o = mustGet(orderNo);
        if (!"SETTLED".equals(o.getStatus())) {
            throw new ApiException(400, "订单未结算: " + o.getStatus());
        }
        // 找持仓
        List<Holding> holdings = holdingMapper.findByCustomerIdAndStatus(o.getCustomerId(), "HOLDING");
        Holding target = holdings.stream()
            .filter(h -> orderNo.equals(o.getOrderNo()))
            .findFirst()
            .orElseThrow(() -> new ApiException(404, "持仓不存在"));

        target.setStatus("REDEEMED");
        target.setEndDate(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        holdingMapper.updateById(target);

        o.setStatus("REDEEMED");
        o.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(o);

        log.info("[Order] {} 赎回成功，本金 {} 元", orderNo, target.getPrincipal());
        return target;
    }

    // ============= 复合：一键购买（评估+合规+支付） =============

    @Transactional
    public Map<String, Object> oneClickBuy(String customerId, String productCode, double amount,
                                            String agentUsername, Map<String, Object> riskAnswers) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 0) 风险评估（如提供了问卷答案且客户未评估）
        RiskAssessment ra = riskService.getLatestValid(customerId);
        if (ra == null && riskAnswers != null) {
            Map<String, Object> assessResult = riskService.assess(customerId, riskAnswers);
            result.put("riskAssessment", assessResult);
        } else if (ra == null) {
            throw new ApiException(400, "请先完成风险评估（POST /im/risk/assess）");
        } else {
            result.put("riskAssessment", Map.of("score", ra.getScore(), "riskLevel", ra.getRiskLevel()));
        }

        // 1) 创建订单
        FinancialOrder order = createOrder(customerId, productCode, amount,
            agentUsername != null ? "AGENT" : "CUSTOMER", agentUsername);
        result.put("orderNo", order.getOrderNo());

        // 2) 风险评估（order 级别）
        assessRisk(order.getOrderNo());

        // 3) 合规检查
        order = runCompliance(order.getOrderNo());
        result.put("complianceResult", order.getComplianceResult());
        result.put("complianceRemark", order.getComplianceRemark());
        if ("REJECTED".equals(order.getStatus())) {
            result.put("success", false);
            result.put("message", "合规检查未通过");
            return result;
        }

        // 4) 支付
        order = pay(order.getOrderNo(), "MOCK_BANK");
        result.put("status", order.getStatus());
        result.put("paidAt", order.getPaidAt());
        result.put("success", true);
        result.put("message", "购买成功");
        return result;
    }

    // ============= 查询 =============

    public FinancialOrder get(String orderNo) {
        return mustGet(orderNo);
    }

    public List<FinancialOrder> listByCustomer(String customerId) {
        return orderMapper.findByCustomerIdOrderByIdDesc(customerId);
    }

    public List<Holding> listHoldings(String customerId) {
        return holdingMapper.findByCustomerId(customerId);
    }

    // ============= 工具 =============

    private FinancialOrder mustGet(String orderNo) {
        FinancialOrder o = orderMapper.findByOrderNo(orderNo);
        if (o == null) throw new ApiException(404, "订单不存在: " + orderNo);
        return o;
    }

    private LocalDate computeEndDate(String period) {
        LocalDate now = LocalDate.now();
        return switch (period) {
            case "30D" -> now.plusDays(30);
            case "90D" -> now.plusDays(90);
            case "1Y" -> now.plusYears(1);
            case "5Y" -> now.plusYears(5);
            default -> null;
        };
    }
}