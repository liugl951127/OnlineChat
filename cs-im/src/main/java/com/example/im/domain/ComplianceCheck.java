package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合规检查记录
 */
@Data
@TableName("compliance_check")
public class ComplianceCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    /** 实名认证 (TINYINT 0/1) */
    private Integer identityCheck;
    /** 风险评估 */
    private Integer riskCheck;
    /** 适当性匹配 */
    private Integer suitabilityCheck;
    /** 反洗钱 */
    private Integer amlCheck;
    /** 总体结果：PASS / REJECTED */
    private String overallResult;
    private String remark;
    private LocalDateTime createdAt;
    @TableLogic

    private Integer deleted;
}