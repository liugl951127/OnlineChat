package com.example.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * 微信公众号 access_token + jsapi_ticket 全局缓存（Redis）。
 *
 * <p>access_token 有效期 7200 秒，提前 5 分钟刷新避免边界问题。
 * jsapi_ticket 有效期 7200 秒，依赖 access_token 换发。
 *
 * <p>沙箱 mock 模式：直接返回 mock token/ticket，零外部依赖。
 *
 * <p>配置项：
 * <pre>
 * wechat:
 *   oa:
 *     app-id: ${WX_OA_APP_ID:demo-app-id}
 *     app-secret: ${WX_OA_APP_SECRET:demo-app-secret}
 *     mock: ${WX_OA_MOCK:true}
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatAccessTokenCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Value("${wechat.oa.app-id:demo-app-id}")
    private String appId;
    @Value("${wechat.oa.app-secret:demo-app-secret}")
    private String appSecret;
    @Value("${wechat.oa.mock:true}")
    private boolean mock;

    private static final String ACCESS_TOKEN_KEY = "wx:oa:access_token";
    private static final String JSAPI_TICKET_KEY = "wx:oa:jsapi_ticket";
    /** 提前 5 分钟刷新 */
    private static final Duration TTL = Duration.ofSeconds(7200 - 300);

    /** 获取全局 access_token（自动刷新） */
    public String getAccessToken() {
        if (mock) {
            return "MOCK_ACCESS_TOKEN_" + UUID.randomUUID().toString().substring(0, 8);
        }
        String cached = redis.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + appId
                + "&secret=" + appSecret;
        try {
            JsonNode n = json.readTree(http.getForObject(url, String.class));
            String token = n.path("access_token").asText("");
            if (token.isEmpty()) {
                log.error("[WechatOA] getAccessToken failed: {}", n.toString());
                throw new RuntimeException("get access_token failed: " + n.toString());
            }
            redis.opsForValue().set(ACCESS_TOKEN_KEY, token, TTL);
            log.info("[WechatOA] refreshed access_token");
            return token;
        } catch (Exception e) {
            log.error("[WechatOA] getAccessToken error", e);
            throw new RuntimeException("get access_token error: " + e.getMessage());
        }
    }

    /** 获取 JS-SDK jsapi_ticket（自动刷新，依赖 access_token） */
    public String getJsapiTicket() {
        if (mock) {
            return "MOCK_JSAPI_TICKET_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        }
        String cached = redis.opsForValue().get(JSAPI_TICKET_KEY);
        if (cached != null) {
            return cached;
        }
        String token = getAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket"
                + "?access_token=" + token
                + "&type=jsapi";
        try {
            JsonNode n = json.readTree(http.getForObject(url, String.class));
            String ticket = n.path("ticket").asText("");
            if (ticket.isEmpty() || !"0".equals(n.path("errcode").asText("0"))) {
                log.error("[WechatOA] getJsapiTicket failed: {}", n.toString());
                throw new RuntimeException("get jsapi_ticket failed: " + n.toString());
            }
            redis.opsForValue().set(JSAPI_TICKET_KEY, ticket, TTL);
            log.info("[WechatOA] refreshed jsapi_ticket");
            return ticket;
        } catch (Exception e) {
            log.error("[WechatOA] getJsapiTicket error", e);
            throw new RuntimeException("get jsapi_ticket error: " + e.getMessage());
        }
    }

    /** 强制清空缓存（运营调试用） */
    public void clear() {
        redis.delete(ACCESS_TOKEN_KEY);
        redis.delete(JSAPI_TICKET_KEY);
    }
}