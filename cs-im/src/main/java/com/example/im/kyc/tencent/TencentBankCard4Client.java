package com.example.im.kyc.tencent;

import com.example.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 腾讯云银行卡四要素鉴权客户端（v2.2.0）
 *
 * <p>API：Verify4Element 或 BankCard4EVerification（云支付 cpdp 模块）
 *
 * <p>实际生产可用：
 * <ul>
 *   <li>腾讯云 - 云支付 - 银行卡四要素鉴权（企业认证）</li>
 *   <li>阿里云 - 金融云 - 银行卡四要素（企业认证）</li>
 *   <li>银联开放平台（最权威，需资质）</li>
 * </ul>
 *
 * <p>这里以腾讯云云支付 Verify4E 元素为例（API ID 占位，按腾讯云实际接口调整）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentBankCard4Client {

    private final TencentAiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 四要素鉴权
     *
     * @param cardNo   银行卡号
     * @param realName 持卡人姓名
     * @param idCardNo 身份证号
     * @param mobile   预留手机号
     * @return { verified: bool, bankCode, bankName, cardType }
     */
    public Map<String, Object> verify4Element(String cardNo, String realName, String idCardNo, String mobile) {
        if (isDemo(props.getSecretId()) || isDemo(props.getSecretKey())) {
            throw new ApiException(503, "腾讯云未配置：请设置 tencent.ai.secret-id/secret-key 或将 kyc.mock.bank-card 改为 true");
        }

        long ts = Instant.now().getEpochSecond();
        // 腾讯云云支付 Verify4E 元素接口（实际请求结构以官方文档为准）
        String payload = String.format(
            "{\"CardNo\":\"%s\",\"RealName\":\"%s\",\"IdCardNo\":\"%s\",\"Mobile\":\"%s\"}",
            cardNo, realName, idCardNo, mobile);

        TencentCloudSigner signer = new TencentCloudSigner(
            props.getSecretId(),
            props.getSecretKey(),
            "cpdp",
            "cpdp.tencentcloudapi.com",
            props.getRegion(),
            "Verify4E",  // 占位 action，按实际接口名改
            payload,
            ts);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
        h.set("Host", "cpdp.tencentcloudapi.com");
        h.set("X-TC-Action", "Verify4E");
        h.set("X-TC-Version", "2019-08-20");
        h.set("X-TC-Timestamp", String.valueOf(ts));
        h.set("Authorization", signer.authorization());

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.exchange(
                "https://cpdp.tencentcloudapi.com",
                HttpMethod.POST,
                new HttpEntity<>(payload, h),
                String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode respNode = root.path("Response");
            JsonNode respError = respNode.path("Error");
            if (!respError.isMissingNode()) {
                String code = respError.path("Code").asText();
                String msg = respError.path("Message").asText();
                throw new ApiException(502, "腾讯云四要素鉴权失败：" + code + " " + msg);
            }

            // 假设返回结构：Result.VerifyResult ("PASS" / "FAIL")
            String verifyResult = respNode.path("Result").path("VerifyResult").asText("FAIL");

            Map<String, Object> result = new HashMap<>();
            result.put("verified", "PASS".equals(verifyResult));
            result.put("detail", "腾讯云鉴权 " + verifyResult);
            result.put("bankCode", "");
            result.put("bankName", "");
            result.put("cardType", "DEBIT");

            log.info("[TencentBank4] 鉴权完成 result={} cardNo={}", verifyResult,
                cardNo.substring(0, 4) + "**********" + cardNo.substring(cardNo.length() - 4));
            return result;
        } catch (HttpClientErrorException e) {
            throw new ApiException(502, "腾讯云 HTTP " + e.getStatusCode() + "：" + e.getResponseBodyAsString());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "腾讯云四要素鉴权异常：" + e.getMessage());
        }
    }

    private boolean isDemo(String s) {
        return s == null || s.isBlank() || s.startsWith("demo-");
    }
}