package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.im.domain.BankCard;
import com.example.im.domain.KycApplication;
import com.example.im.domain.KycRiskStatement;
import com.example.im.service.KycService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * KYC REST 接口（v2.0.0）
 *
 * <p>客户视角（前端 KYC 流程页调用）：
 * <ul>
 *   <li>POST /im/kyc/create            — 创建申请单</li>
 *   <li>POST /im/kyc/upload-idcard     — 身份证 OCR</li>
 *   <li>POST /im/kyc/liveness          — 活体检测</li>
 *   <li>POST /im/kyc/face-match        — 人脸比对</li>
 *   <li>POST /im/kyc/submit-video      — 提交视频双录</li>
 *   <li>POST /im/kyc/submit-audit      — 提交审核</li>
 *   <li>POST /im/kyc/bind-card         — 银行卡四要素绑卡</li>
 *   <li>GET  /im/kyc/my                — 我的最新申请</li>
 *   <li>GET  /im/kyc/status            — 状态查询</li>
 * </ul>
 *
 * <p>坐席视角（KYC 审核工作台）：
 * <ul>
 *   <li>GET  /im/kyc/audit/queue        — 待审核列表</li>
 *   <li>GET  /im/kyc/audit/mine         — 我的审核历史</li>
 *   <li>GET  /im/kyc/audit/{appNo}      — 审核详情</li>
 *   <li>POST /im/kyc/audit/{appNo}/approve — 审核通过</li>
 *   <li>POST /im/kyc/audit/{appNo}/reject  — 审核拒绝</li>
 * </ul>
 *
 * <p>公开（前端 KYC 页面用）：
 * <ul>
 *   <li>GET  /im/kyc/statements         — 风险声明列表</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/im/kyc")
@RequiredArgsConstructor
public class KycController {

    /** KYC 业务 */
    private final KycService kycService;

    // ============ 客户视角 ============

    /**
     * 创建申请单
     */
    @PostMapping("/create")
    public ApiResponse<KycApplication> create() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.createApplication(ctx.getUserId()));
    }

    /**
     * 上传身份证 OCR
     */
    @PostMapping("/upload-idcard")
    public ApiResponse<KycApplication> uploadIdcard(@RequestBody IdcardReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        if (req.getFrontImgUrl() == null || req.getBackImgUrl() == null) {
            throw new ApiException(400, "身份证正反面图必填");
        }
        // 创建申请单（如不存在）+ 上传
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null || "REJECTED".equals(app.getStatus()) || "COMPLETED".equals(app.getStatus())) {
            app = kycService.createApplication(ctx.getUserId());
        }
        return ApiResponse.ok(kycService.uploadIdCard(app.getApplicationNo(), req.getFrontImgUrl(), req.getBackImgUrl()));
    }

    /**
     * 活体检测
     */
    @PostMapping("/liveness")
    public ApiResponse<KycApplication> liveness(@RequestBody LivenessReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null) throw new ApiException(400, "请先上传身份证");
        return ApiResponse.ok(kycService.checkLiveness(app.getApplicationNo(), req.getFaceImgUrl(), req.getActions()));
    }

    /**
     * 人脸比对
     */
    @PostMapping("/face-match")
    public ApiResponse<KycApplication> faceMatch() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null) throw new ApiException(400, "请先完成前面步骤");
        return ApiResponse.ok(kycService.matchFace(app.getApplicationNo()));
    }

    /**
     * 提交视频双录
     */
    @PostMapping("/submit-video")
    public ApiResponse<KycApplication> submitVideo(@RequestBody VideoReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null) throw new ApiException(400, "请先完成前面步骤");
        return ApiResponse.ok(kycService.submitVideo(app.getApplicationNo(),
                req.getStatementCode(), req.getVideoBase64(), req.getDurationSec()));
    }

    /**
     * 提交审核
     */
    @PostMapping("/submit-audit")
    public ApiResponse<KycApplication> submitAudit() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null) throw new ApiException(400, "请先完成前面步骤");
        return ApiResponse.ok(kycService.submitForAudit(app.getApplicationNo()));
    }

    /**
     * 银行卡四要素绑卡
     */
    @PostMapping("/bind-card")
    public ApiResponse<KycApplication> bindCard(@RequestBody BindCardReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        KycApplication app = kycService.findLatestByCustomer(ctx.getUserId());
        if (app == null) throw new ApiException(400, "请先完成前面步骤");
        return ApiResponse.ok(kycService.bindBankCard(app.getApplicationNo(),
                req.getCardNo(), req.getCardName(), req.getIdCardNo(), req.getMobile()));
    }

    /**
     * 我的最新申请
     */
    @GetMapping("/my")
    public ApiResponse<KycApplication> myLatest() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.findLatestByCustomer(ctx.getUserId()));
    }

    /**
     * 我的状态（便捷）
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> myStatus() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        String status = kycService.getCustomerKycStatus(ctx.getUserId());
        return ApiResponse.ok(Map.of("customerId", ctx.getUserId(), "status", status, "completed", "COMPLETED".equals(status)));
    }

    // ============ 坐席视角 ============

    /**
     * 待审核列表
     */
    @GetMapping("/audit/queue")
    public ApiResponse<List<KycApplication>> auditQueue() {
        return ApiResponse.ok(kycService.listPendingAudits());
    }

    /**
     * 我的审核历史
     */
    @GetMapping("/audit/mine")
    public ApiResponse<List<KycApplication>> myAudits() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.listMyAudits(ctx.getUserId()));
    }

    /**
     * 审核详情
     */
    @GetMapping("/audit/{applicationNo}")
    public ApiResponse<KycApplication> auditDetail(@PathVariable String applicationNo) {
        return ApiResponse.ok(kycService.findByApplicationNo(applicationNo));
    }

    /**
     * 审核通过
     */
    @PostMapping("/audit/{applicationNo}/approve")
    public ApiResponse<KycApplication> approve(@PathVariable String applicationNo,
                                                @RequestBody ApproveReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.approve(applicationNo, ctx.getUserId(), req.getScore(), req.getRemark()));
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/audit/{applicationNo}/reject")
    public ApiResponse<KycApplication> reject(@PathVariable String applicationNo, @RequestBody RejectReq req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.reject(applicationNo, ctx.getUserId(), req.getReason()));
    }

    // ============ 公开 ============

    /**
     * 风险声明列表（前端朗读页面用）
     */
    @GetMapping("/statements")
    public ApiResponse<List<KycRiskStatement>> statements() {
        return ApiResponse.ok(kycService.listRiskStatements());
    }

    /**
     * 我的绑卡列表
     */
    @GetMapping("/bank-cards")
    public ApiResponse<List<BankCard>> bankCards() {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        return ApiResponse.ok(kycService.listBankCards(ctx.getUserId()));
    }

    // ============ 请求体 ============

    @Data
    public static class IdcardReq {
        private String frontImgUrl;
        private String backImgUrl;
    }

    @Data
    public static class LivenessReq {
        /** 活体照片 URL */
        private String faceImgUrl;
        /** 动作序列 */
        private List<String> actions;
    }

    @Data
    public static class VideoReq {
        /** 声明代码 */
        private String statementCode;
        /** 视频 Base64 */
        private String videoBase64;
        /** 时长（秒） */
        private Integer durationSec;
    }

    @Data
    public static class BindCardReq {
        private String cardNo;
        private String cardName;
        private String idCardNo;
        private String mobile;
    }

    @Data
    public static class ApproveReq {
        private BigDecimal score;
        private String remark;
    }

    @Data
    public static class RejectReq {
        private String reason;
    }
}