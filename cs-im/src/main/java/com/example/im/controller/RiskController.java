package com.example.im.controller;

import com.example.common.ApiException;                  // 业务异常
import com.example.common.ApiResponse;                  // 统一返回
import com.example.common.SecurityContextHolder;         // 当前用户
import com.example.im.domain.RiskAssessment;            // 风险评估实体
import com.example.im.service.RiskAssessmentService;    // 风险评估业务
import lombok.RequiredArgsConstructor;                   // Lombok 注入
import org.springframework.web.bind.annotation.*;        // Spring MVC 注解

import java.util.Map;                                    // 通用 map

/**
 * 风险评估 REST 接口（v1.8.0）
 *
 * <p>5 题问卷 → 加权评分 → 写入 risk_assessment（有效期 1 年）
 */
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    /** 风险评估业务 */
    private final RiskAssessmentService riskService;

    /**
     * 提交问卷
     *
     * @param body { customerId?, answers: { age, income, experience, preference, ratio } }
     * @return { score, riskLevel, expiresAt }
     */
    @PostMapping("/assess")
    public ApiResponse<Map<String, Object>> assess(@RequestBody Map<String, Object> body) {
        // 1) 取 customerId（优先用 body 的，其次用当前登录用户）
        String customerId = (String) body.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            var ctx = SecurityContextHolder.current();
            if (ctx == null) throw new ApiException(401, "未登录");
            customerId = ctx.getUserId();
        }

        // 2) 取答案
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = (Map<String, Object>) body.get("answers");
        if (answers == null) throw new ApiException(400, "answers 必填");

        // 3) 评估
        return ApiResponse.ok(riskService.assess(customerId, answers));
    }

    /**
     * 查最新有效评估（强制从 SecurityContext 取，防越权）
     */
    @GetMapping("/latest")
    public ApiResponse<RiskAssessment> latest(@RequestParam(required = false) String customerId) {
        var ctx = SecurityContextHolder.current();
        String cid = ctx != null ? ctx.getUserId() : null;
        if (cid == null) throw new ApiException(401, "未登录");
        boolean isStaff = ctx != null && ("AGENT".equals(ctx.getRole()) || "ADMIN".equals(ctx.getRole()));
        if (customerId != null && !customerId.isBlank() && !customerId.equals(cid)) {
            if (!isStaff) throw new ApiException(403, "无权查询他人风险评估");
            cid = customerId;
        }
        return ApiResponse.ok(riskService.getLatestValid(cid));
    }
}