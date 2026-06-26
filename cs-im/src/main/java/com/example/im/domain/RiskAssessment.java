package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户风险评估记录
 *
 * <p>5 题问卷：年龄 / 收入 / 投资经验 / 风险偏好 / 资产占比
 * 分数 0-100，对应风险等级 CONSERVATIVE / MODERATE / AGGRESSIVE
 */
@Data
@TableName("risk_assessment")
public class RiskAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerId;
    /** 评估分数 0-100 */
    private Integer score;
    /** 风险等级 */
    private String riskLevel;
    /** 评估问卷答案 JSON */
    private String answersJson;
    /** 评估有效期（默认 1 年） */
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}