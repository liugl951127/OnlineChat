package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 电子合同 (v2.2.43)
 *
 * <p>金融产品购买必须先签约。流程：
 * <ol>
 *   <li>客户付款成功后，生成合同（模板填充客户信息）</li>
 *   <li>客户用私钥对合同 hash 签名（前端 Web Crypto API RSA-PSS）</li>
 *   <li>前端把签名提交到 /order/{orderNo}/sign</li>
 *   <li>后端验证签名 → status=CONTRACT_SIGNED</li>
 * </ol>
 *
 * <p>合同 hash = SHA256(合同内容 JSON)，用客户公钥对应的 RSA-PSS 签名。
 * 客户端公钥在 wechat_user 表（暂未存，v2.2.43 用 mock）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("contract")
public class Contract {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合同编号 (CT + 时间戳) */
    private String contractNo;

    /** 关联订单号 */
    private String orderNo;

    /** 客户 customerId */
    private String customerId;

    /** 产品代码 */
    private String productCode;

    /** 合同模板 ID */
    private String templateId;

    /** 合同内容（完整 HTML 或 JSON） */
    private String content;

    /** 内容 SHA256 hash (VARCHAR(64)) */
    private String contentHash;

    /** 客户公钥（PEM 格式，RSA-2048） */
    private String customerPublicKey;

    /** 客户签名（base64 编码 RSA-PSS-SHA256, TEXT） */
    private String customerSignature;

    /** 签约时间 */
    private LocalDateTime signedAt;

    /** 客户 IP */
    private String signedIp;

    /** 状态：DRAFT / SIGNED / REVOKED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}