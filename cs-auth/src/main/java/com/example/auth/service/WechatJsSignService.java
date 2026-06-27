package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信公众号 JS-SDK wx.config 签名生成。
 *
 * <p>前端调用流程：
 * <pre>
 *   GET /auth/wechat-oa/js-sign?url=https://yourdomain.com/page
 *   ← { appId, timestamp, nonceStr, signature }
 *   wx.config({ appId, timestamp, nonceStr, signature, ... })
 * </pre>
 *
 * <p>签名算法（SHA1）：
 *   signature = sha1(jsapi_ticket + noncestr + timestamp + url)
 *
 * <p>注意：url 必须是当前页面完整 URL（不含 # 及其后面），前端传 encodeURIComponent 后的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatJsSignService {

    private final WechatAccessTokenCache tokenCache;

    @Value("${wechat.oa.app-id:demo-app-id}")
    private String appId;

    /**
     * 生成 wx.config 签名
     *
     * @param url 当前页面完整 URL（不含 #）
     * @return { appId, timestamp, nonceStr, signature }
     */
    public Map<String, String> sign(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        String ticket = tokenCache.getJsapiTicket();
        long timestamp = System.currentTimeMillis() / 1000;
        String nonceStr = UUID.randomUUID().toString().replace("-", "");

        // 注意顺序：jsapi_ticket, noncestr, timestamp, url
        String s = "jsapi_ticket=" + ticket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;
        String signature = sha1(s);

        Map<String, String> r = new HashMap<>();
        r.put("appId", appId);
        r.put("timestamp", String.valueOf(timestamp));
        r.put("nonceStr", nonceStr);
        r.put("signature", signature);
        log.info("[WechatOA-JS] sign url={} sig={}", url, signature);
        return r;
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha1 error", e);
        }
    }
}