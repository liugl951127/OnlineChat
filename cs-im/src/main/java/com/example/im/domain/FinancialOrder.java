package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金融产品订单（MyBatis Plus，匹配 V2.0.0 schema）
 *
 * <p>状态机：
 * <pre>
 *   DRAFT → RISK_ASSESSED → COMPLIANCE_PASSED → PAYING → SETTLED → REDEEMED
 *                                              REJECTED
 * </pre>
 */
@Data
@TableName("financial_order")
public class FinancialOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String customerId;
    /** AGENT 协助下单时记录坐席 */
    private String agentUsername;
    private String productCode;
    private String productName;
    private Double amount;
    private String paymentMethod;
    /** 订单状态 */
    private String status;
    /** 风险评估结果：CONSERVATIVE / MODERATE / AGGRESSIVE */
    private String riskLevel;
    /** 风险评估分数（0-100） */
    private Integer riskScore;
    /** 合规检查结果 */
    private String complianceResult;
    /** 合规备注 */
    private String complianceRemark;
    private String initiatorRole;
    /** 支付时间 */
    private LocalDateTime paidAt;
    /** 完成时间 */
    private LocalDateTime completedAt;
    /** 赎回时间 */
    private LocalDateTime redeemedAt;
    /** 合同编号 (v2.2.43) */
    private String contractNo;
    /** 合同 hash */
    private String contractHash;
    /** 签约时间 */
    private LocalDateTime signedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic

    private Integer deleted;
}