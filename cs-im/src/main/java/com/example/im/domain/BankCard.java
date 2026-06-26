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
 * 客户绑卡（cs_im.bank_card 表）
 *
 * <p>v2.0.0 银行卡四要素鉴权。
 *
 * <p>四要素：卡号 / 持卡人姓名 / 身份证号 / 预留手机号
 */
@Data
@TableName("bank_card")
public class BankCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户 ID */
    private String customerId;

    /** 卡号（加密存储，AES） */
    private String cardNoEnc;

    /** 卡号掩码（如 **** **** **** 1234） */
    private String cardNoMasked;

    /** 持卡人姓名 */
    private String cardName;

    /** 银行代码（ICBC/CCB/ABC/CMB 等） */
    private String bankCode;

    /** 银行名称 */
    private String bankName;

    /** 卡类型：DEBIT 借记 / CREDIT 贷记 */
    private String cardType;

    /** 预留手机号 */
    private String mobile;

    /** 是否默认卡 */
    private Integer isDefault;

    /** 四要素验证是否通过 */
    private Integer verified;

    /** 验证时间 */
    private LocalDateTime verifiedAt;

    /** 状态：BOUND / UNBOUND / FROZEN */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}