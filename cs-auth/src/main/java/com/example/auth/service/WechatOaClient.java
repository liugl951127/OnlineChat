package com.example.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号 OAuth 客户端。
 *
 * <p>流程：客户点击登录 → 跳转微信授权页 → 微信回调带 code → 后端用 code 换 access_token + openid
 *
 * <p>配置项（application.yml）：
 * <pre>
 * wechat:
 *   oa:
 *     app-id: xxx
 *     app-secret: xxx
 *     redirect-uri: https://yourdomain.com/auth/wechat-oa/callback
 *     mock: true   # 没有真实 app 时打开 mock 模式
 * </pre>
 */
@Slf4j
@Service
public class WechatOaClient {

    @Value("${wechat.oa.app-id:demo-oa-appid}")
    private String appId;
    @Value("${wechat.oa.app-secret:demo-oa-secret}")
    private String appSecret;
    @Value("${wechat.oa.mock:true}")
    private boolean mock;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    /** 第一步：生成授权 URL */
    public String authorizeUrl(String redirectUri, String state, String scope) {
        String s = scope == null ? "snsapi_base" : scope; // base=静默，userinfo=弹窗
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + appId
                + "&redirect_uri=" + url(redirectUri)
                + "&response_type=code"
                + "&scope=" + s
                + "&state=" + state
                + "#wechat_redirect";
    }

    /** 第二步：用 code 换 openid + access_token */
    public Map<String, String> exchangeCode(String code) {
        if (mock) {
            log.info("[WechatOA-MOCK] code={}", code);
            Map<String, String> r = new HashMap<>();
            r.put("openid", "oa-mock-" + code);
            r.put("unionid", "union-mock-" + code);
            r.put("access_token", "mock-token");
            r.put("nickname", "客户" + code.substring(0, Math.min(4, code.length())));
            r.put("avatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + code);
            return r;
        }
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + appId
                + "&secret=" + appSecret
                + "&code=" + code
                + "&grant_type=authorization_code";
        try {
            JsonNode n = json.readTree(http.getForObject(url, String.class));
            return Map.of(
                    "openid", str(n, "openid"),
                    "unionid", str(n, "unionid"),
                    "access_token", str(n, "access_token")
            );
        } catch (Exception e) {
            log.error("[WechatOA] exchange failed", e);
            throw new RuntimeException("微信授权失败: " + e.getMessage());
        }
    }

    private static String str(JsonNode n, String k) { return n.has(k) ? n.get(k).asText() : ""; }
    private static String url(String s) { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8); }
}