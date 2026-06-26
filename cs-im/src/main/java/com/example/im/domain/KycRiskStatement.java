package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险声明库（cs_im.kyc_risk_statement 表）
 *
 * <p>v2.0.0 双录话术：监管要求客户朗读的标准声明。
 *
 * <p>客户必须完整朗读声明 + 视频录制，后台审核确认。
 */
@Data
@TableName("kyc_risk_statement")
public class KycRiskStatement {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 声明代码（业务唯一，如 RS-INVEST-001） */
    private String code;

    /** 标题 */
    private String title;

    /** 全文（客户朗读） */
    private String content;

    /** 分类：GENERAL/INVESTMENT/INSURANCE */
    private String category;

    /** 要求朗读时长（秒） */
    private Integer requiredDurationSec;

    /** 状态：DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}