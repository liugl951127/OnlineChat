package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 银行卡四要素鉴权服务（v2.0.0 Mock 实现）
 *
 * <p>四要素：
 * <ol>
 *   <li>银行卡号</li>
 *   <li>持卡人姓名</li>
 *   <li>身份证号</li>
 *   <li>银行预留手机号</li>
 * </ol>
 *
 * <p>真实生产应调用：
 * <ul>
 *   <li>银联四要素鉴权 API</li>
 *   <li>网联四要素 API</li>
 *   <li>商业银行开放平台</li>
 * </ul>
 */
@Slf4j
@Service
public class BankCardVerifyService {

    private static final Random RND = new Random();

    /**
     * 四要素鉴权
     *
     * @param cardNo      银行卡号
     * @param cardName    持卡人姓名
     * @param idCardNo    身份证号
     * @param mobile      银行预留手机号
     * @return { verified: bool, bankCode: "", bankName: "", cardType: "" }
     */
    public Map<String, Object> verify4Elements(String cardNo, String cardName, String idCardNo, String mobile) {
        log.info("[BankCard-Mock] 四要素鉴权 cardNo={} name={} idCard={} mobile={}",
                maskCard(cardNo), cardName, maskIdCard(idCardNo), maskMobile(mobile));

        // 1) 校验：四要素都必填
        if (isEmpty(cardNo) || isEmpty(cardName) || isEmpty(idCardNo) || isEmpty(mobile)) {
            return Map.of("verified", false, "detail", "四要素不全");
        }

        // 2) 校验：手机号格式
        if (!mobile.matches("^1[3-9]\\d{9}$")) {
            return Map.of("verified", false, "detail", "手机号格式错误");
        }

        // 3) Mock 处理耗时
        try { Thread.sleep(200 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        // 4) 100% 通过（Mock 模式，生产由真实接口决定）
        boolean verified = true;

        // 5) Mock 银行识别（按卡号前 6 位）
        String bankCode = "ICBC";
        String bankName = "工商银行";
        String cardType = cardNo.startsWith("4") ? "CREDIT" : "DEBIT";

        Map<String, Object> result = new HashMap<>();
        result.put("verified", verified);
        result.put("bankCode", bankCode);
        result.put("bankName", bankName);
        result.put("cardType", cardType);
        result.put("detail", verified ? "鉴权通过" : "鉴权失败（姓名/卡号/身份证不匹配）");

        log.info("[BankCard-Mock] 结果: verified={}, bankCode={}", verified, bankCode);
        return result;
    }

    /** 卡号掩码：6222 **** **** 1234 */
    public static String maskCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) return cardNo;
        return cardNo.substring(0, 4) + " **** **** " + cardNo.substring(cardNo.length() - 4);
    }

    /** 身份证掩码：110105********1234 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /** 手机号掩码：138****5678 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }
}