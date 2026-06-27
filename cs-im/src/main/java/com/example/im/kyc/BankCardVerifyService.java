package com.example.im.kyc;

import com.example.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 银行卡四要素鉴权服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>生产：银联 / 网联四要素鉴权 API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankCardVerifyService {

    private static final Random RND = new Random();

    private final KycMockProperties mockProps;

    public Map<String, Object> verify4Elements(String cardNo, String cardName, String idCardNo, String mobile) {
        if (mockProps.isBankCard()) {
            return verifyMock(cardNo, cardName, idCardNo, mobile);
        }
        return verifyReal(cardNo, cardName, idCardNo, mobile);
    }

    private Map<String, Object> verifyMock(String cardNo, String cardName, String idCardNo, String mobile) {
        log.info("[BankCard-Mock] 四要素鉴权 cardNo={} name={} idCard={} mobile={}",
                maskCard(cardNo), cardName, maskIdCard(idCardNo), maskMobile(mobile));

        if (isEmpty(cardNo) || isEmpty(cardName) || isEmpty(idCardNo) || isEmpty(mobile)) {
            return Map.of("verified", false, "detail", "四要素不全");
        }
        if (!mobile.matches("^1[3-9]\\d{9}$")) {
            return Map.of("verified", false, "detail", "手机号格式错误");
        }
        if (cardNo.length() < 16) {
            return Map.of("verified", false, "detail", "银行卡号格式错误");
        }

        try { Thread.sleep(200 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("verified", true);
        result.put("bankCode", "ICBC");
        result.put("bankName", "中国工商银行");
        result.put("cardType", "DEBIT");
        result.put("detail", "Mock 通过");
        return result;
    }

    private Map<String, Object> verifyReal(String cardNo, String cardName, String idCardNo, String mobile) {
        log.info("[BankCard-Real] 银联四要素鉴权 cardNo={}", maskCard(cardNo));

        // 生产实现：银联 UnionPayClient.verify4Elements()
        // UnionPayClient client = new UnionPayClient(merchantId, certPath, certPwd);
        // Verify4ElementsRequest req = new Verify4ElementsRequest(cardNo, cardName, idCardNo, mobile);
        // Verify4ElementsResponse resp = client.verify(req);
        // return Map.of(
        //     "verified", resp.isVerified(),
        //     "bankCode", resp.getBankCode(),
        //     "bankName", resp.getBankName(),
        //     "cardType", resp.getCardType()
        // );

        throw new ApiException(501, "银行卡四要素真实 API 未配置：请实现 BankCardVerifyService.verifyReal() 或将 kyc.mock.bank-card 改为 true");
    }

    // ---- 脱敏工具 ----

    public static String maskCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) return "****";
        return cardNo.substring(0, 4) + "**********" + cardNo.substring(cardNo.length() - 4);
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return "****";
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) return "****";
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}