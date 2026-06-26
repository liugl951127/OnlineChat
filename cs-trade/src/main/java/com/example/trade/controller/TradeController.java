package com.example.trade.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.common.SensitiveUtils;
import com.example.trade.domain.Bill;
import com.example.trade.domain.Product;
import com.example.trade.repo.BillRepo;
import com.example.trade.repo.ProductRepo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {

    private final BillRepo billRepo;
    private final ProductRepo productRepo;

    /** 账单查询（脱敏后返回） */
    @GetMapping("/bills/recent")
    public ApiResponse<List<Map<String, Object>>> recentBills(@RequestParam String customerId,
                                                              @RequestParam(defaultValue = "7") int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        List<Bill> list = billRepo.findByCustomerIdAndBizDateGreaterThanEqualOrderByBizDateDesc(customerId, from);
        List<Map<String, Object>> result = new ArrayList<>();
        double total = 0;
        for (Bill b : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", b.getBizDate().toString());
            m.put("type", b.getBizType());
            m.put("amount", b.getAmount());
            m.put("status", b.getStatus());
            m.put("remark", SensitiveUtils.maskName(b.getRemark()));
            result.add(m);
            total += b.getAmount();
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("count", list.size());
        summary.put("total", total);
        // 富文本结构：items + summary
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(summary);
        data.addAll(result);
        return ApiResponse.ok(data);
    }

    /** 产品列表（按关键词） */
    @GetMapping("/products")
    public ApiResponse<List<Map<String, Object>>> products(@RequestParam(required = false) String keyword) {
        List<Product> list = keyword == null || keyword.isBlank()
                ? productRepo.findAll()
                : productRepo.findByNameContainingIgnoreCase(keyword);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Product p : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("desc", p.getDescription());
            m.put("rate", p.getRate());
            m.put("period", p.getPeriod());
            m.put("riskLevel", p.getRiskLevel());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 交易下单（前置：未登录用户拒绝 / 联网核查 mock 通过才记 SUCCESS） */
    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> create(@RequestBody OrderReq req) {
        String cid = SecurityContextHolder.requireUserId();
        if (req.getBizType() == null) throw new ApiException(400, "bizType 必填");
        if (req.getAmount() == null || req.getAmount() <= 0) throw new ApiException(400, "金额非法");
        // 联网核查（mock：FAILxxx 拒绝）
        if (req.getIdCardNo() != null && req.getIdCardNo().startsWith("FAIL")) {
            return ApiResponse.fail(403, "联网核查未通过");
        }
        Bill b = billRepo.save(Bill.builder()
                .customerId(cid)
                .bizType(req.getBizType())
                .amount(req.getAmount())
                .bizDate(LocalDate.now())
                .status("SUCCESS")
                .remark(req.getRemark() == null ? "" : req.getRemark())
                .build());
        Map<String, Object> r = new HashMap<>();
        r.put("billId", b.getId());
        r.put("bizType", b.getBizType());
        r.put("amount", b.getAmount());
        r.put("status", b.getStatus());
        return ApiResponse.ok(r);
    }

    @Data
    public static class OrderReq {
        private String bizType;
        private Double amount;
        private String idCardNo;
        private String remark;
    }
}