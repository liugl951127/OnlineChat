package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.Contract;
import com.example.im.service.ContractService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 电子合同 REST 接口 (v2.2.43)
 *
 * <p>流程:
 *   POST /contract/generate     - 生成合同（订单合规通过后）
 *   POST /contract/sign         - 客户签署（RSA-PSS 签名）
 *   GET  /contract/{contractNo} - 查询合同
 */
@Slf4j
@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/generate")
    public ApiResponse<Contract> generate(@RequestBody GenerateReq req) {
        return ApiResponse.ok(contractService.generateContract(req.getOrderNo(), req.getTemplateId()));
    }

    @PostMapping("/sign")
    public ApiResponse<Contract> sign(@RequestBody SignReq req, HttpServletRequest request) {
        String ip = getClientIp(request);
        return ApiResponse.ok(contractService.signContract(
                req.getContractNo(), req.getPublicKey(), req.getSignature(), ip));
    }

    @GetMapping("/{contractNo}")
    public ApiResponse<Contract> get(@PathVariable String contractNo) {
        return ApiResponse.ok(contractService.findByContractNo(contractNo));
    }

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return req.getRemoteAddr();
    }

    @Data
    public static class GenerateReq {
        private String orderNo;
        private String templateId = "TPL-FIN-001";
    }

    @Data
    public static class SignReq {
        private String contractNo;
        private String publicKey;
        private String signature;
    }
}