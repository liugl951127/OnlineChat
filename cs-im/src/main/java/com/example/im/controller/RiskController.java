package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.RiskAssessment;
import com.example.im.service.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskAssessmentService riskService;

    /** 提交问卷 → 评估 */
    @PostMapping("/assess")
    public ApiResponse<Map<String, Object>> assess(@RequestParam String customerId,
                                                   @RequestBody Map<String, Object> answers) {
        return ApiResponse.ok(riskService.assess(customerId, answers));
    }

    /** 查最新评估 */
    @GetMapping("/latest")
    public ApiResponse<RiskAssessment> latest(@RequestParam String customerId) {
        return ApiResponse.ok(riskService.getLatestValid(customerId));
    }
}