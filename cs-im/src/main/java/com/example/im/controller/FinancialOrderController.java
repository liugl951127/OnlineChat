package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.FinancialOrder;
import com.example.im.domain.Holding;
import com.example.im.service.FinancialOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 金融订单 REST 接口（v1.8.0 + v1.9.0 增强）
 *
 * <p>5 步状态机：
 * <pre>
 *   createOrder → DRAFT
 *   assessRisk  → RISK_ASSESSED
 *   runCompliance → COMPLIANCE_PASSED / REJECTED
 *   pay → SETTLED（生成 Holding）
 *   redeem → REDEEMED
 * </pre>
 *
 * <p>v1.9.0 新增：
 * <ul>
 *   <li>支持坐席协助下单（agentUsername 字段）</li>
 *   <li>支持客户自助一键购买（oneClickBuy）</li>
 *   <li>返回结构带 complianceRemark 方便前端展示</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class FinancialOrderController {

    /** 金融订单业务层 */
    private final FinancialOrderService orderService;
    private final com.example.im.service.ContractService contractService;

    /**
     * 创建订单（草稿状态）
     */
    @PostMapping("/create")
    public ApiResponse<FinancialOrder> create(@RequestBody CreateReq req) {
        // 1) 取当前用户（默认 CUSTOMER，若带 agentUsername 则角色是 AGENT）
        var ctx = SecurityContextHolder.current();
        String operatorId = ctx != null ? ctx.getUserId() : null;

        // 2) 校验客户 ID
        String customerId = req.getCustomerId() != null ? req.getCustomerId() : operatorId;
        if (customerId == null) throw new ApiException(400, "customerId 必填");

        // 3) 判断角色
        String initiatorRole = req.getAgentUsername() != null ? "AGENT" : "CUSTOMER";

        // 4) 调 Service
        return ApiResponse.ok(orderService.createOrder(
                customerId, req.getProductCode(), req.getAmount(),
                initiatorRole, req.getAgentUsername()));
    }

    /**
     * 风险评估
     */
    @PostMapping("/{orderNo}/assess")
    public ApiResponse<FinancialOrder> assess(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.assessRisk(orderNo));
    }

    /**
     * 合规检查（4 道关）
     */
    @PostMapping("/{orderNo}/compliance")
    public ApiResponse<FinancialOrder> compliance(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.runCompliance(orderNo));
    }

    /**
     * 生成合同（v2.2.43 合规检查通过后调用）
     */
    @PostMapping("/{orderNo}/contract/generate")
    public ApiResponse<com.example.im.domain.Contract> generateContract(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.generateContract(orderNo));
    }

    /**
     * 客户签约 → 订单状态变 CONTRACT_SIGNED
     */
    @PostMapping("/{orderNo}/contract/sign")
    public ApiResponse<FinancialOrder> signContract(@PathVariable String orderNo,
                                                    @RequestBody SignContractReq req) {
        // 1) 调 ContractService 签名
        contractService.signContract(req.getContractNo(), req.getPublicKey(),
                req.getSignature(), req.getSignedIp());
        // 2) 订单状态跳 CONTRACT_SIGNED
        return ApiResponse.ok(orderService.markContractSigned(orderNo));
    }

    /**
     * 支付（mock）
     */
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<FinancialOrder> pay(@PathVariable String orderNo,
                                           @RequestParam(defaultValue = "MOCK_BANK") String method) {
        return ApiResponse.ok(orderService.pay(orderNo, method));
    }

    /**
     * 一键购买复合方法
     *
     * <p>流程：评估(若无) → 创建订单 → 风险评估 → 合规检查 → 支付
     * 任一失败立即返回，结果中 success 标志 + message 说明
     */
    @PostMapping("/one-click-buy")
    public ApiResponse<Map<String, Object>> oneClickBuy(@RequestBody OneClickReq req) {
        // 1) 校验参数
        if (req.getCustomerId() == null || req.getProductCode() == null || req.getAmount() == null) {
            throw new ApiException(400, "customerId/productCode/amount 必填");
        }

        // 2) 调 Service
        Map<String, Object> result = orderService.oneClickBuy(
                req.getCustomerId(), req.getProductCode(), req.getAmount(),
                req.getAgentUsername(), req.getRiskAnswers());
        return ApiResponse.ok(result);
    }

    /**
     * 赎回
     */
    @PostMapping("/{orderNo}/redeem")
    public ApiResponse<Holding> redeem(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.redeem(orderNo));
    }

    /**
     * 查询订单
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<FinancialOrder> get(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.get(orderNo));
    }

    /**
     * 客户的所有订单（强制从 SecurityContext 取，禁止请求参数覆盖防越权）
     */
    @GetMapping("/list")
    public ApiResponse<List<FinancialOrder>> listByCustomer(@RequestParam(required = false) String customerId) {
        var ctx = SecurityContextHolder.current();
        String cid = ctx != null ? ctx.getUserId() : null;
        if (cid == null) throw new ApiException(401, "未登录");
        // 坐席/管理员可查询任意客户（用 X-User-Role 区分）
        boolean isStaff = ctx != null && ("AGENT".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole()));
        if (customerId != null && !customerId.isBlank() && !customerId.equals(cid)) {
            if (!isStaff) throw new ApiException(403, "无权查询他人订单");
            cid = customerId;
        }
        return ApiResponse.ok(orderService.listByCustomer(cid));
    }

    /**
     * 客户的所有持仓（强制从 SecurityContext 取，禁止请求参数覆盖防越权）
     */
    @GetMapping("/holdings")
    public ApiResponse<List<Holding>> holdings(@RequestParam(required = false) String customerId) {
        var ctx = SecurityContextHolder.current();
        String cid = ctx != null ? ctx.getUserId() : null;
        if (cid == null) throw new ApiException(401, "未登录");
        boolean isStaff = ctx != null && ("AGENT".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole()));
        if (customerId != null && !customerId.isBlank() && !customerId.equals(cid)) {
            if (!isStaff) throw new ApiException(403, "无权查询他人持仓");
            cid = customerId;
        }
        return ApiResponse.ok(orderService.listHoldings(cid));
    }

    /**
     * 创建订单请求体
     */
    @Data
    public static class CreateReq {
        /** 客户 ID（可空，默认当前登录用户） */
        private String customerId;
        /** 产品代码 */
        private String productCode;
        /** 购买金额 */
        private Double amount;
        /** 坐席协助下单（坐席账号），可空 */
        private String agentUsername;
    }

    /**
     * 一键购买请求体
     */
    @Data
    public static class OneClickReq {
        /** 客户 ID */
        private String customerId;
        /** 产品代码 */
        private String productCode;
        /** 购买金额 */
        private Double amount;
        /** 坐席协助下单（可空） */
        private String agentUsername;
        /** 5 题问卷答案（可空，若客户已评估过则跳过） */
        private Map<String, Object> riskAnswers;
    }

    /**
     * 签约请求体 (v2.2.43)
     */
    @Data
    public static class SignContractReq {
        private String contractNo;
        private String publicKey;
        private String signature;
        private String signedIp;
    }
}