package com.example.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 企业微信 OAuth 客户端。
 *
 * <p>流程：
 * <ol>
 *   <li>前端跳转 https://login.work.weixin.qq.com/wwlogin/sso/login?login_type=CorpApp&appid=...&redirect_uri=...&state=...</li>
 *   <li>企微回调带 code + state</li>
 *   <li>后端用 code 调 https://qyapi.weixin.qq.com/cgi-bin/service/getuserinfo3rd 获取 userid</li>
 *   <li>再用 userid 调 https://qyapi.weixin.qq.com/cgi-bin/service/getuserdetail3rd 获取用户信息</li>
 * </ol>
 *
 * <p>Mock 模式下直接生成伪 userid。
 */
@Slf4j
@Service
public class WechatWorkClient {

    @Value("${wechat.work.corp-id:demo-corp}")
    private String corpId;
    @Value("${wechat.work.agent-id:demo-agent}")
    private String agentId;
    @Value("${wechat.work.app-secret:demo-secret}")
    private String appSecret;
    @Value("${wechat.work.mock:true}")
    private boolean mock;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    public String authorizeUrl(String redirectUri, String state) {
        if (mock) {
            // mock 模式：跳过企微服务器，直接生成 mock code 跳到前端 callback
            String mockCode = "MOCK-WORK-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[WechatWork-MOCK] authorizeUrl → mock code={} redirect={}", mockCode, redirectUri);
            String finalRedirect = redirectUri;
            if (redirectUri.endsWith("/callback-json")) {
                finalRedirect = redirectUri.replace("/callback-json", "/callback");
            }
            String sep = finalRedirect.contains("?") ? "&" : "?";
            return finalRedirect + sep + "code=" + mockCode + "&state=" + state + "&mock=true";
        }
        return "https://login.work.weixin.qq.com/wwlogin/sso/login"
                + "?login_type=CorpApp"
                + "&appid=" + corpId
                + "&redirect_uri=" + enc(redirectUri)
                + "&state=" + state
                + "&agentid=" + agentId;
    }

    /** 用 code 换 userid + 用户详情 */
    public Map<String, String> exchangeCode(String code) {
        if (mock) {
            Map<String, String> r = new HashMap<>();
            r.put("userid", "ww-mock-" + code);
            r.put("name", "员工" + code.substring(0, Math.min(4, code.length())));
            r.put("avatar", "https://api.dicebear.com/7.x/avataaars/svg?seed=" + code);
            r.put("mobile", "1380000" + code.substring(0, Math.min(4, code.length())));
            return r;
        }
        try {
            // 1) 拿 access_token
            String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + appSecret;
            String token = json.readTree(http.getForObject(accessTokenUrl, String.class)).get("access_token").asText();
            // 2) getuserinfo3rd
            String u1 = "https://qyapi.weixin.qq.com/cgi-bin/service/getuserinfo3rd?access_token=" + token + "&code=" + code;
            JsonNode n1 = json.readTree(http.getForObject(u1, String.class));
            String userid = n1.path("UserId").asText();
            // 3) getuserdetail3rd
            String u2 = "https://qyapi.weixin.qq.com/cgi-bin/service/getuserdetail3rd?access_token=" + token + "&userid=" + userid;
            JsonNode n2 = json.readTree(http.getForObject(u2, String.class));
            Map<String, String> r = new HashMap<>();
            r.put("userid", userid);
            r.put("name", n2.path("name").asText());
            r.put("avatar", n2.path("avatar").asText());
            r.put("mobile", n2.path("mobile").asText());
            return r;
        } catch (Exception e) {
            log.error("[WechatWork] exchange failed", e);
            throw new RuntimeException("企微授权失败: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}