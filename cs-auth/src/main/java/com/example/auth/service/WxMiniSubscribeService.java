package com.example.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序订阅消息推送。
 *
 * <p>订阅消息特点：
 * <ul>
 *   <li>用户必须先在客户端 wx.requestSubscribeMessage 授权</li>
 *   <li>一次性订阅：每次发送都需要用户重新授权</li>
 *   <li>长期订阅：仅部分行业（金融/医疗/政府）支持</li>
 * </ul>
 *
 * <p>access_token 全局缓存，与公众号共用 WechatAccessTokenCache。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxMiniSubscribeService {

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Value("${wechat.mini.app-id:wx-mini-appid}")
    private String appId;
    @Value("${wechat.mini.app-secret:wx-mini-secret}")
    private String appSecret;
    @Value("${wechat.oa.mock:true}")
    private boolean mock;

    private static final String MINI_TOKEN_KEY = "wx:mini:access_token";
    private static final Duration TTL = Duration.ofSeconds(7200 - 300);

    /** 获取小程序全局 access_token */
    public String getAccessToken() {
        if (mock) {
            return "MOCK_MINI_TOKEN_" + System.currentTimeMillis();
        }
        String cached = redis.opsForValue().get(MINI_TOKEN_KEY);
        if (cached != null) return cached;
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + appId
                + "&secret=" + appSecret;
        try {
            Map<?, ?> resp = http.getForObject(url, Map.class);
            String token = (String) resp.get("access_token");
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("get mini access_token failed: " + resp);
            }
            redis.opsForValue().set(MINI_TOKEN_KEY, token, TTL);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("get mini access_token error: " + e.getMessage());
        }
    }

    /**
     * 发送小程序订阅消息
     *
     * @param openid 用户 openid
     * @param templateId 订阅消息模板 ID
     * @param data 模板数据 {key: {value}}
     * @param page 跳转页面（可选）
     * @return { errcode, errmsg }
     */
    public Map<String, Object> send(String openid, String templateId,
                                     Map<String, Object> data,
                                     String page) {
        if (mock) {
            log.info("[WxMini-SUB-MOCK] send → openid={} tpl={} data={}",
                    openid, templateId, data);
            Map<String, Object> r = new HashMap<>();
            r.put("errcode", 0);
            r.put("errmsg", "ok");
            return r;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);
        if (page != null) body.put("page", page);
        body.put("miniprogram_state", "formal");  // developer / trial / formal
        body.put("lang", "zh_CN");
        try {
            String resp = http.postForObject(url, json.writeValueAsString(body), String.class);
            log.info("[WxMini-SUB] send → openid={} tpl={} resp={}", openid, templateId, resp);
            return json.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("[WxMini-SUB] send failed", e);
            throw new RuntimeException("send mini subscribe msg error: " + e.getMessage());
        }
    }
}