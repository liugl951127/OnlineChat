package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.Contract;
import com.example.im.domain.FinancialOrder;
import com.example.im.domain.Product;
import com.example.im.repo.ContractMapper;
import com.example.im.repo.FinancialOrderMapper;
import com.example.im.repo.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 电子合同服务 (v2.2.43)
 *
 * <p>真实签名：前端用 Web Crypto API RSA-PSS-SHA256 签名
 * <p>沙箱 mock：跳过签名验证，只记录 hash（生产必须强制验签）
 *
 * <p>合同模板 ID:
 *   TPL-FIN-001 - 金融产品购买合同
 *   TPL-FIN-002 - 金融产品赎回协议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractMapper contractMapper;
    private final FinancialOrderMapper orderMapper;
    private final ProductMapper productMapper;

    /**
     * 生成合同（订单状态 COMPLIANCE_PASSED 后调用）
     */
    @Transactional
    public Contract generateContract(String orderNo, String templateId) {
        FinancialOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) throw new ApiException(404, "订单不存在: " + orderNo);
        if (!"COMPLIANCE_PASSED".equals(order.getStatus())) {
            throw new ApiException(400, "订单未通过合规检查: " + order.getStatus());
        }
        Product product = productMapper.findByCode(order.getProductCode());
        if (product == null) throw new ApiException(404, "产品不存在");

        String content = renderTemplate(templateId, order, product);
        String hash = sha256(content);

        Contract c = Contract.builder()
                .contractNo("CT" + System.currentTimeMillis())
                .orderNo(orderNo)
                .customerId(order.getCustomerId())
                .productCode(order.getProductCode())
                .templateId(templateId)
                .content(content)
                .contentHash(hash)
                .status("DRAFT")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        contractMapper.insert(c);
        log.info("[Contract] generated contractNo={} orderNo={} hash={}", c.getContractNo(), orderNo, hash);
        return c;
    }

    /**
     * 客户签约（提交 RSA-PSS 签名）
     *
     * @param publicKey 客户公钥（PEM 格式）
     * @param signature 签名（base64）
     * @param signedIp  客户 IP
     */
    @Transactional
    public Contract signContract(String contractNo, String publicKey, String signature, String signedIp) {
        Contract c = contractMapper.findByContractNo(contractNo);
        if (c == null) throw new ApiException(404, "合同不存在: " + contractNo);
        if ("SIGNED".equals(c.getStatus())) {
            throw new ApiException(400, "合同已签约，请勿重复签署");
        }
        if ("REVOKED".equals(c.getStatus())) {
            throw new ApiException(400, "合同已撤销");
        }

        // 沙箱 mock：不真实验签，只记录
        // 生产必须 verify(c.contentHash, signature, publicKey)
        boolean verified = verifySignature(c.getContentHash(), signature, publicKey);

        if (!verified) {
            throw new ApiException(400, "签名验证失败");
        }

        c.setCustomerPublicKey(publicKey);
        c.setCustomerSignature(signature);
        c.setSignedIp(signedIp);
        c.setSignedAt(LocalDateTime.now());
        c.setStatus("SIGNED");
        c.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(c);

        // 更新订单状态
        FinancialOrder order = orderMapper.findByOrderNo(c.getOrderNo());
        if (order != null) {
            order.setStatus("CONTRACT_SIGNED");
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        log.info("[Contract] signed contractNo={} customer={}", contractNo, c.getCustomerId());
        return c;
    }

    /**
     * 沙箱 mock: 永远返回 true
     * 生产实现: java.security.Signature.verify(contentHash, signature, publicKey)
     */
    private boolean verifySignature(String contentHash, String signature, String publicKey) {
        // 真实验证逻辑（生产环境）：
        // Signature sig = Signature.getInstance("SHA256withRSA/PSS");
        // sig.initVerify(publicKey);  // X509EncodedKeySpec
        // sig.update(contentHash.getBytes());
        // return sig.verify(Base64.getDecoder().decode(signature));
        //
        // 沙箱跳过验证（mock=true 时）
        log.info("[Contract-MOCK] signature verified (sandbox)");
        return true;
    }

    /** 渲染合同模板 */
    private String renderTemplate(String templateId, FinancialOrder order, Product product) {
        return """
                ════════════════════════════════════════════
                【%s】
                
                合同编号: %s
                签署日期: %s
                
                甲方（客户）: %s
                乙方（金融机构）: OnlineChat 金融部
                
                鉴于甲方拟购买乙方发行的金融产品，
                双方经友好协商，达成如下协议：
                
                ────────────────────────────────────
                产品信息
                ────────────────────────────────────
                产品代码:     %s
                产品名称:     %s
                产品类型:     %s
                风险等级:     %s
                预期年化:     %s
                
                ────────────────────────────────────
                交易详情
                ────────────────────────────────────
                订单编号:     %s
                购买金额:     ¥%s
                起息日期:     %s
                到期日期:     %s
                收益结算:     到期一次性还本付息
                
                ────────────────────────────────────
                风险提示
                ────────────────────────────────────
                1. 理财非存款，投资需谨慎
                2. 甲方已通过风险评估问卷，了解产品风险
                3. 投资盈亏由甲方自行承担
                4. 乙方已充分披露产品风险
                
                ────────────────────────────────────
                客户声明
                ────────────────────────────────────
                本人 %s 已阅读并理解本合同全部条款，
                自愿购买上述金融产品，自担投资风险。
                
                甲方（电子签名）: ____________________
                签约时间: %s
                客户 IP: %s
                ════════════════════════════════════════════
                """.formatted(
                product.getName(),
                "CT" + System.currentTimeMillis(),
                LocalDateTime.now(),
                order.getCustomerId(),
                product.getProductCode(), product.getName(),
                product.getProductType(), product.getRiskLevel(),
                product.getYieldRate(),
                order.getOrderNo(), order.getAmount(),
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(1),
                order.getCustomerId(),
                LocalDateTime.now(),
                "客户 IP"
        );
    }

    /** 查询合同 */
    public Contract findByContractNo(String contractNo) {
        Contract c = contractMapper.findByContractNo(contractNo);
        if (c == null) throw new ApiException(404, "合同不存在: " + contractNo);
        return c;
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 error", e);
        }
    }
}