package com.example.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * GitHub OAuth 2.0
 *
 * <p>流程：
 * <ol>
 *   <li>客户端跳转 {@code authorizeUrl}（带 state）</li>
 *   <li>用户授权后 GitHub 回调 {@code /auth/github/callback}（带 code + state）</li>
 *   <li>用 code 换 access_token</li>
 *   <li>用 access_token 拉取 user info（id/login/name/avatar_url/email）</li>
 * </ol>
 *
 * <p>Mock 模式：未配置 client-id 时返回伪数据。
 */
@Slf4j
@Service
public class GithubOAuthClient {

    @Value("${oauth.github.client-id:demo-github-client-id}")
    private String clientId;
    @Value("${oauth.github.client-secret:demo-github-secret}")
    private String clientSecret;
    @Value("${oauth.github.mock:true}")
    private boolean mock;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    /** 第一步：生成授权 URL */
    public String authorizeUrl(String redirectUri, String state, String scope) {
        String s = scope == null ? "read:user user:email" : scope;
        return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", s)
                .queryParam("state", state)
                .queryParam("allow_signup", "true")
                .build().encode().toUriString();
    }

    /** 第二步：code -> access_token */
    public String exchangeCode(String code) {
        if (mock) return "mock-github-token-" + code;
        String url = "https://github.com/login/oauth/access_token"
                + "?client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code=" + enc(code);
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "application/json");
        try {
            ResponseEntity<String> r = http.exchange(url, HttpMethod.POST, new HttpEntity<>(h), String.class);
            JsonNode n = json.readTree(r.getBody());
            return n.path("access_token").asText("");
        } catch (Exception e) {
            log.error("[GitHub-OAuth] exchange failed", e);
            throw new RuntimeException("GitHub 授权失败: " + e.getMessage());
        }
    }

    /** 第三步：access_token -> user info */
    public Map<String, String> fetchUser(String accessToken) {
        if (mock) {
            Map<String, String> m = new HashMap<>();
            m.put("id", accessToken.replace("mock-github-token-", ""));
            m.put("login", "github-user-" + accessToken.substring(accessToken.length() - 6));
            m.put("name", "GitHub 用户");
            m.put("avatar_url", "https://api.dicebear.com/7.x/identicon/svg?seed=" + accessToken);
            m.put("email", "");
            return m;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("Authorization", "Bearer " + accessToken);
            h.set("Accept", "application/json");
            h.set("User-Agent", "OnlineChat");
            ResponseEntity<String> r = http.exchange("https://api.github.com/user", HttpMethod.GET, new HttpEntity<>(h), String.class);
            JsonNode n = json.readTree(r.getBody());
            Map<String, String> m = new HashMap<>();
            m.put("id", String.valueOf(n.path("id").asLong()));
            m.put("login", n.path("login").asText(""));
            m.put("name", n.path("name").asText(n.path("login").asText("")));
            m.put("avatar_url", n.path("avatar_url").asText(""));
            m.put("email", n.path("email").asText(""));
            return m;
        } catch (Exception e) {
            log.error("[GitHub-OAuth] fetchUser failed", e);
            throw new RuntimeException("GitHub 用户信息获取失败: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}