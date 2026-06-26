package com.example.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 消息签名：HMAC-SHA256(sessionId|fromUser|content|ts, secret)
 * 客户端验证消息确实来自本系统，防止伪造
 */
public final class MessageSignature {
    private MessageSignature() {}

    public static String sign(String secret, Long sessionId, String fromUser, String content, long ts) {
        try {
            String data = sessionId + "|" + fromUser + "|" + content + "|" + ts;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean verify(String secret, String sig, Long sessionId, String fromUser, String content, long ts) {
        if (sig == null || sig.isEmpty()) return false;
        String expected = sign(secret, sessionId, fromUser, content, ts);
        return constantTimeEquals(expected, sig);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}