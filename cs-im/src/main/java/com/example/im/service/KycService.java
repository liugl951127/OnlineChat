package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.BankCard;
import com.example.im.domain.KycApplication;
import com.example.im.domain.KycRiskStatement;
import com.example.im.domain.KycVideoRecord;
import com.example.im.kyc.BankCardVerifyService;
import com.example.im.kyc.FaceMatchService;
import com.example.im.kyc.LivenessService;
import com.example.im.kyc.OcrService;
import com.example.im.kyc.RiskControlService;
import com.example.im.kyc.VideoRecordService;
import com.example.im.repo.BankCardMapper;
import com.example.im.repo.KycApplicationMapper;
import com.example.im.repo.KycRiskStatementMapper;
import com.example.im.repo.KycVideoRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KYC 业务服务（v2.0.0）
 *
 * <p>完整 KYC 流程：
 * <ol>
 *   <li>创建申请单（INIT）</li>
 *   <li>身份证 OCR 上传（OCR_UPLOADED）</li>
 *   <li>活体检测（LIVENESS_PASSED）</li>
 *   <li>人脸比对（FACE_MATCHED）</li>
 *   <li>视频双录（VIDEO_RECORDED）</li>
 *   <li>提交审核（AUDITING）</li>
 *   <li>坐席审核通过（APPROVED）</li>
 *   <li>银行卡四要素鉴权（BANK_BINDING）</li>
 *   <li>完成（COMPLETED）</li>
 * </ol>
 *
 * <p>任何环节失败 → REJECTED，需重新提交申请。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final KycApplicationMapper appMapper;
    private final KycRiskStatementMapper statementMapper;
    private final KycVideoRecordMapper videoMapper;
    private final BankCardMapper bankCardMapper;

    private final OcrService ocrService;
    private final LivenessService livenessService;
    private final FaceMatchService faceMatchService;
    private final VideoRecordService videoRecordService;
    private final BankCardVerifyService bankCardService;
    private final RiskControlService riskControlService;

    private final AuditService auditService;

    /**
     * 创建 KYC 申请单
     */
    @Transactional
    public KycApplication createApplication(String customerId) {
        // 1) 校验：是否已有未完成的申请
        appMapper.findLatestByCustomerId(customerId).ifPresent(existing -> {
            if (!"REJECTED".equals(existing.getStatus())
                    && !"COMPLETED".equals(existing.getStatus())) {
                throw new ApiException(400, "客户已有进行中的 KYC 申请: " + existing.getApplicationNo());
            }
        });

        // 2) 创建
        KycApplication app = new KycApplication();
        app.setApplicationNo("KYC" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        app.setCustomerId(customerId);
        app.setStatus("INIT");
        app.setBlacklistHit(0);
        appMapper.insert(app);

        // 3) 审计
        auditService.log("KYC_CREATE", "KYC_APPLICATION", app.getApplicationNo(),
                customerId, "CUSTOMER", null);

        log.info("[KYC] 创建申请单: {} customer={}", app.getApplicationNo(), customerId);
        return app;
    }

    /**
     * 步骤 1：身份证 OCR 上传
     */
    @Transactional
    public KycApplication uploadIdCard(String applicationNo, String frontImgUrl, String backImgUrl) {
        KycApplication app = mustGet(applicationNo);
        if (!"INIT".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可上传身份证: " + app.getStatus());
        }

        // 1) OCR 识别
        Map<String, Object> ocrResult = ocrService.recognize(frontImgUrl, backImgUrl);

        // 2) 填充实体
        app.setIdCardName((String) ocrResult.get("name"));
        app.setIdCardGender((String) ocrResult.get("gender"));
        app.setIdCardNation((String) ocrResult.get("nation"));
        app.setIdCardBirth((String) ocrResult.get("birth"));
        app.setIdCardAddress((String) ocrResult.get("address"));
        app.setIdCardIssue((String) ocrResult.get("issue"));
        app.setIdCardValidity((String) ocrResult.get("validity"));
        app.setIdCardNo((String) ocrResult.get("cardNo"));
        app.setIdCardFrontImg(frontImgUrl);
        app.setIdCardBackImg(backImgUrl);
        app.setOcrRawJson(ocrResult.toString());
        app.setStatus("OCR_UPLOADED");

        // 3) 风控初评
        Map<String, Object> risk = riskControlService.assess(app.getIdCardNo(), null, app.getCustomerId());
        app.setRiskLevel((String) risk.get("riskLevel"));
        Object blacklistObj = risk.get("blacklistHit");
        boolean blacklistHit = Boolean.TRUE.equals(blacklistObj) ||
                (blacklistObj instanceof Integer && (Integer) blacklistObj == 1);
        app.setBlacklistHit(blacklistHit ? 1 : 0);

        // 4) 黑名单直接拒绝
        if (blacklistHit) {
            app.setStatus("REJECTED");
            auditService.log("KYC_REJECT", "KYC_APPLICATION", app.getApplicationNo(),
                    "SYSTEM", "SYSTEM", "命中黑名单");
        }

        appMapper.updateById(app);
        auditService.log("KYC_OCR", "KYC_APPLICATION", app.getApplicationNo(),
                app.getCustomerId(), "CUSTOMER", "OCR 完成");
        return app;
    }

    /**
     * 步骤 2：活体检测
     */
    @Transactional
    public KycApplication checkLiveness(String applicationNo, String faceImgUrl, List<String> actions) {
        KycApplication app = mustGet(applicationNo);
        if (!"OCR_UPLOADED".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可活体检测: " + app.getStatus());
        }

        // 1) 活体检测
        Map<String, Object> result = livenessService.check(actions);
        boolean passed = (Boolean) result.get("passed");
        double score = (Double) result.get("score");

        app.setFaceImgUrl(faceImgUrl);
        app.setLivenessActions(String.join(",", actions != null ? actions : List.of()));
        app.setLivenessScore(BigDecimal.valueOf(score));
        app.setLivenessPassed(passed ? 1 : 0);

        if (!passed) {
            app.setStatus("REJECTED");
            app.setAuditRemark("活体检测未通过");
        } else {
            app.setStatus("LIVENESS_PASSED");
        }
        appMapper.updateById(app);
        return app;
    }

    /**
     * 步骤 3：人脸比对
     */
    @Transactional
    public KycApplication matchFace(String applicationNo) {
        KycApplication app = mustGet(applicationNo);
        if (!"LIVENESS_PASSED".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可人脸比对: " + app.getStatus());
        }

        Map<String, Object> result = faceMatchService.compare(app.getIdCardFrontImg(), app.getFaceImgUrl());
        double score = (Double) result.get("score");
        boolean passed = (Boolean) result.get("passed");

        app.setFaceMatchScore(BigDecimal.valueOf(score));
        app.setFaceMatchPassed(passed ? 1 : 0);

        if (!passed) {
            app.setStatus("REJECTED");
            app.setAuditRemark("人脸比对未通过");
        } else {
            app.setStatus("FACE_MATCHED");
        }
        appMapper.updateById(app);
        return app;
    }

    /**
     * 步骤 4：提交视频双录
     *
     * @param statementCode 声明代码（如 RS-INVEST-001）
     * @param videoBase64   视频 Base64
     * @param durationSec   时长（秒）
     */
    @Transactional
    public KycApplication submitVideo(String applicationNo, String statementCode,
                                       String videoBase64, int durationSec) {
        KycApplication app = mustGet(applicationNo);
        if (!"FACE_MATCHED".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可提交视频: " + app.getStatus());
        }

        // 1) 查声明
        KycRiskStatement stmt = statementMapper.findByCode(statementCode);
        if (stmt == null) throw new ApiException(404, "风险声明不存在: " + statementCode);

        // 2) 校验时长（不少于 required_duration_sec 的 80%）
        int minRequired = (int) (stmt.getRequiredDurationSec() * 0.8);
        if (durationSec < minRequired) {
            throw new ApiException(400, "视频时长不足（要求 ≥ " + minRequired + " 秒，实际 " + durationSec + " 秒）");
        }

        // 3) 保存视频
        Map<String, Object> videoInfo = videoRecordService.save(app.getId(), stmt.getId(), 1, videoBase64, durationSec);

        // 4) 写视频记录
        KycVideoRecord record = new KycVideoRecord();
        record.setApplicationId(app.getId());
        record.setStatementId(stmt.getId());
        record.setSegmentNo(1);
        record.setVideoUrl((String) videoInfo.get("videoUrl"));
        record.setDurationSec(durationSec);
        record.setFileSizeKb((Integer) videoInfo.get("fileSizeKb"));
        record.setChecksum((String) videoInfo.get("checksum"));
        videoMapper.insert(record);

        // 5) 更新申请
        app.setRiskStatementId(stmt.getId());
        app.setVideoUrl((String) videoInfo.get("videoUrl"));
        app.setVideoDurationSec(durationSec);
        app.setVideoRecordedAt(LocalDateTime.now());
        app.setStatus("VIDEO_RECORDED");

        appMapper.updateById(app);
        return app;
    }

    /**
     * 步骤 5：提交审核
     */
    @Transactional
    public KycApplication submitForAudit(String applicationNo) {
        KycApplication app = mustGet(applicationNo);
        if (!"VIDEO_RECORDED".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可提交审核: " + app.getStatus());
        }
        app.setStatus("AUDITING");
        appMapper.updateById(app);
        auditService.log("KYC_SUBMIT", "KYC_APPLICATION", app.getApplicationNo(),
                app.getCustomerId(), "CUSTOMER", null);
        return app;
    }

    /**
     * 坐席审核通过
     */
    @Transactional
    public KycApplication approve(String applicationNo, String auditorUsername,
                                   BigDecimal auditScore, String remark) {
        KycApplication app = mustGet(applicationNo);
        if (!"AUDITING".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可审核: " + app.getStatus());
        }
        app.setStatus("APPROVED");
        app.setAuditorUsername(auditorUsername);
        app.setAuditScore(auditScore);
        app.setAuditRemark(remark);
        app.setAuditedAt(LocalDateTime.now());
        appMapper.updateById(app);

        auditService.log("KYC_APPROVE", "KYC_APPLICATION", app.getApplicationNo(),
                auditorUsername, "AGENT", remark);
        return app;
    }

    /**
     * 坐席审核拒绝
     */
    @Transactional
    public KycApplication reject(String applicationNo, String auditorUsername, String reason) {
        KycApplication app = mustGet(applicationNo);
        if (!"AUDITING".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可审核: " + app.getStatus());
        }
        app.setStatus("REJECTED");
        app.setAuditorUsername(auditorUsername);
        app.setAuditRemark(reason);
        app.setAuditedAt(LocalDateTime.now());
        appMapper.updateById(app);

        auditService.log("KYC_REJECT", "KYC_APPLICATION", app.getApplicationNo(),
                auditorUsername, "AGENT", reason);
        return app;
    }

    /**
     * 步骤 7：银行卡四要素鉴权 + 绑卡
     */
    @Transactional
    public KycApplication bindBankCard(String applicationNo, String cardNo, String cardName,
                                        String idCardNo, String mobile) {
        KycApplication app = mustGet(applicationNo);
        if (!"APPROVED".equals(app.getStatus())) {
            throw new ApiException(400, "当前状态不可绑卡: " + app.getStatus());
        }

        // 1) 四要素鉴权
        Map<String, Object> verify = bankCardService.verify4Elements(cardNo, cardName, idCardNo, mobile);
        boolean verified = (Boolean) verify.get("verified");
        if (!verified) {
            throw new ApiException(400, "银行卡四要素鉴权失败: " + verify.get("detail"));
        }

        // 2) 保存绑卡记录
        BankCard bc = new BankCard();
        bc.setCustomerId(app.getCustomerId());
        bc.setCardNoEnc(cardNo);  // 实际应加密
        bc.setCardNoMasked(BankCardVerifyService.maskCard(cardNo));
        bc.setCardName(cardName);
        bc.setBankCode((String) verify.get("bankCode"));
        bc.setBankName((String) verify.get("bankName"));
        bc.setCardType((String) verify.get("cardType"));
        bc.setMobile(mobile);
        bc.setIsDefault(1);
        bc.setVerified(1);
        bc.setVerifiedAt(LocalDateTime.now());
        bc.setStatus("BOUND");
        bankCardMapper.insert(bc);

        // 3) 更新申请
        app.setBankCardNo(cardNo);  // 实际应加密
        app.setBankCardName(cardName);
        app.setBankCardMobile(mobile);
        app.setBankCardBindAt(LocalDateTime.now());
        app.setStatus("BANK_BINDING");
        appMapper.updateById(app);

        // 4) 立即完成（生产可加短信确认步骤）
        app.setStatus("COMPLETED");
        app.setCompletedAt(LocalDateTime.now());
        appMapper.updateById(app);

        auditService.log("KYC_BIND_CARD", "KYC_APPLICATION", app.getApplicationNo(),
                app.getCustomerId(), "CUSTOMER", "绑卡成功");
        return app;
    }

    // ============ 查询 ============

    public KycApplication findByApplicationNo(String applicationNo) {
        return appMapper.findByApplicationNo(applicationNo).orElse(null);
    }

    public KycApplication findLatestByCustomer(String customerId) {
        return appMapper.findLatestByCustomerId(customerId).orElse(null);
    }

    /** 客户当前 KYC 状态（便捷方法） */
    public String getCustomerKycStatus(String customerId) {
        KycApplication app = findLatestByCustomer(customerId);
        if (app == null) return "NOT_STARTED";
        return app.getStatus();
    }

    /** 是否已完成 KYC（购买金融产品前置） */
    public boolean isCompleted(String customerId) {
        return "COMPLETED".equals(getCustomerKycStatus(customerId));
    }

    /** 审核工作台：所有待审核 */
    public List<KycApplication> listPendingAudits() {
        return appMapper.findByStatusOrderByIdDesc("AUDITING");
    }

    /** 我的审核历史 */
    public List<KycApplication> listMyAudits(String auditorUsername) {
        return appMapper.findByAuditorUsernameOrderByIdDesc(auditorUsername);
    }

    /** 风险声明列表 */
    public List<KycRiskStatement> listRiskStatements() {
        return statementMapper.findByStatusOrderBySortOrderAsc("PUBLISHED");
    }

    /** 客户绑卡列表 */
    public List<BankCard> listBankCards(String customerId) {
        return bankCardMapper.findByCustomerIdOrderByIsDefaultDesc(customerId);
    }

    private KycApplication mustGet(String applicationNo) {
        return appMapper.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApiException(404, "KYC 申请不存在: " + applicationNo));
    }
}