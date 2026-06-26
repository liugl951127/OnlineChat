package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.FinancialOrder;
import com.example.im.domain.Holding;
import com.example.im.service.FinancialOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class FinancialOrderController {

    private final FinancialOrderService orderService;

    /** 创建订单（草稿） */
    @PostMapping("/create")
    public ApiResponse<FinancialOrder> create(@RequestBody CreateReq req) {
        return ApiResponse.ok(orderService.createOrder(
            req.getCustomerId(), req.getProductCode(), req.getAmount(),
            req.getAgentUsername() != null ? "AGENT" : "CUSTOMER", req.getAgentUsername()));
    }

    /** 风险评估 */
    @PostMapping("/{orderNo}/assess")
    public ApiResponse<FinancialOrder> assess(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.assessRisk(orderNo));
    }

    /** 合规检查 */
    @PostMapping("/{orderNo}/compliance")
    public ApiResponse<FinancialOrder> compliance(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.runCompliance(orderNo));
    }

    /** 支付 */
    @PostMapping("/{orderNo}/pay")
    public ApiResponse<FinancialOrder> pay(@PathVariable String orderNo,
                                           @RequestParam(defaultValue = "MOCK_BANK") String method) {
        return ApiResponse.ok(orderService.pay(orderNo, method));
    }

    /** 一键购买（评估+合规+支付） */
    @PostMapping("/one-click-buy")
    public ApiResponse<Map<String, Object>> oneClickBuy(@RequestBody OneClickReq req) {
        return ApiResponse.ok(orderService.oneClickBuy(
            req.getCustomerId(), req.getProductCode(), req.getAmount(),
            req.getAgentUsername(), req.getRiskAnswers()));
    }

    /** 赎回 */
    @PostMapping("/{orderNo}/redeem")
    public ApiResponse<Holding> redeem(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.redeem(orderNo));
    }

    /** 查询订单 */
    @GetMapping("/{orderNo}")
    public ApiResponse<FinancialOrder> get(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.get(orderNo));
    }

    /** 客户所有订单 */
    @GetMapping("/list")
    public ApiResponse<List<FinancialOrder>> listByCustomer(@RequestParam String customerId) {
        return ApiResponse.ok(orderService.listByCustomer(customerId));
    }

    /** 客户所有持仓 */
    @GetMapping("/holdings")
    public ApiResponse<List<Holding>> holdings(@RequestParam String customerId) {
        return ApiResponse.ok(orderService.listHoldings(customerId));
    }

    @Data
    public static class CreateReq {
        private String customerId;
        private String productCode;
        private Double amount;
        private String agentUsername;  // 可选：坐席协助下单
    }

    @Data
    public static class OneClickReq {
        private String customerId;
        private String productCode;
        private Double amount;
        private String agentUsername;
        private Map<String, Object> riskAnswers;  // 可选：5 题问卷答案
    }
}