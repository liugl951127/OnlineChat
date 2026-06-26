package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KYC 申请单（cs_im.kyc_application 表）
 *
 * <p>v2.0.0 完整 KYC 核心实体。
 *
 * <p>状态机：
 * <pre>
 *   INIT
 *     ↓ 身份证 OCR 上传
 *   OCR_UPLOADED
 *     ↓ 活体检测
 *   LIVENESS_PASSED
 *     ↓ 人脸比对
 *   FACE_MATCHED
 *     ↓ 视频双录
 *   VIDEO_RECORDED
 *     ↓ 提交审核
 *   AUDITING
 *     ↓ 审核通过
 *   APPROVED  (or REJECTED)
 *     ↓ 绑卡
 *   BANK_BINDING
 *     ↓ 完成
 *   COMPLETED
 * </pre>
 */
@Data
@TableName("kyc_application")
public class KycApplication {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请单号（业务唯一） */
    private String applicationNo;

    /** 客户 ID */
    private String customerId;

    /** 状态 */
    private String status;

    // ============ 身份证 OCR ============

    /** 身份证号（加密存储） */
    private String idCardNo;

    /** 身份证姓名 */
    private String idCardName;

    /** 性别 M/F */
    private String idCardGender;

    /** 民族 */
    private String idCardNation;

    /** 生日 YYYY-MM-DD */
    private String idCardBirth;

    /** 地址 */
    private String idCardAddress;

    /** 签发机关 */
    private String idCardIssue;

    /** 有效期 */
    private String idCardValidity;

    /** 身份证正面图 URL */
    private String idCardFrontImg;

    /** 身份证反面图 URL */
    private String idCardBackImg;

    /** OCR 原始 JSON */
    private String ocrRawJson;

    // ============ 人脸比对 ============

    /** 活体照片 URL */
    private String faceImgUrl;

    /** 相似度 0-100 */
    private BigDecimal faceMatchScore;

    /** 是否通过 */
    private Integer faceMatchPassed;

    // ============ 活体检测 ============

    /** 活体动作序列 */
    private String livenessActions;

    /** 活体分数 */
    private BigDecimal livenessScore;

    /** 是否通过 */
    private Integer livenessPassed;

    // ============ 视频双录 ============

    /** 风险声明 ID */
    private Long riskStatementId;

    /** 双录视频 URL */
    private String videoUrl;

    /** 视频时长（秒） */
    private Integer videoDurationSec;

    /** 录制完成时间 */
    private LocalDateTime videoRecordedAt;

    // ============ 审核 ============

    /** 审核员 */
    private String auditorUsername;

    /** 审核评分 */
    private BigDecimal auditScore;

    /** 审核备注 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    // ============ 银行卡 ============

    /** 卡号（加密） */
    private String bankCardNo;

    /** 持卡人姓名 */
    private String bankCardName;

    /** 预留手机号 */
    private String bankCardMobile;

    /** 绑卡时间 */
    private LocalDateTime bankCardBindAt;

    // ============ 风控 ============

    /** 风险等级 LOW/MID/HIGH */
    private String riskLevel;

    /** 是否命中黑名单 */
    private Integer blacklistHit;

    // ============ 通用 ============

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** KYC 完成时间 */
    private LocalDateTime completedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}