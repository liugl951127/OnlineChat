package com.example.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
 * Google OAuth 2.0
 *
 * <p>流程：
 * <ol>
 *   <li>客户端跳转 {@code authorizeUrl}（带 state + nonce）</li>
 *   <li>用户授权后 Google 回调 {@code /auth/google/callback}（带 code + state）</li>
 *   <li>用 code 换 access_token + id_token</li>
 *   <li>解析 id_token 或调 userinfo 端点获取用户信息</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthClient {
    private final RestTemplate http;


    @Value("${oauth.google.client-id:demo-google-client-id}")
    private String clientId;
    @Value("${oauth.google.client-secret:demo-google-secret}")
    private String clientSecret;
    @Value("${oauth.google.mock:true}")
    private boolean mock;

    
    private final ObjectMapper json = new ObjectMapper();

    public String authorizeUrl(String redirectUri, String state, String scope) {
        String s = scope == null ? "openid email profile" : scope;
        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", s)
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build().encode().toUriString();
    }

    /** code -> access_token */
    public String exchangeCode(String code, String redirectUri) {
        if (mock) return "mock-google-token-" + code;
        String url = UriComponentsBuilder.fromHttpUrl("https://oauth2.googleapis.com/token")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .queryParam("redirect_uri", redirectUri)
                .build().encode().toUriString();
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "application/json");
        try {
            ResponseEntity<String> r = http.exchange(url, HttpMethod.POST, new HttpEntity<>(h), String.class);
            JsonNode n = json.readTree(r.getBody());
            return n.path("access_token").asText("");
        } catch (Exception e) {
            log.error("[Google-OAuth] exchange failed", e);
            throw new RuntimeException("Google 授权失败: " + e.getMessage());
        }
    }

    /** access_token -> user info */
    public Map<String, String> fetchUser(String accessToken) {
        if (mock) {
            Map<String, String> m = new HashMap<>();
            String token = accessToken.replace("mock-google-token-", "");
            m.put("sub", "google-" + token);
            m.put("name", "Google 用户");
            m.put("email", "");
            m.put("picture", "https://api.dicebear.com/7.x/initials/svg?seed=" + token);
            return m;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("Authorization", "Bearer " + accessToken);
            ResponseEntity<String> r = http.exchange("https://www.googleapis.com/oauth2/v2/userinfo",
                    HttpMethod.GET, new HttpEntity<>(h), String.class);
            JsonNode n = json.readTree(r.getBody());
            Map<String, String> m = new HashMap<>();
            m.put("sub", n.path("id").asText(""));
            m.put("name", n.path("name").asText(""));
            m.put("email", n.path("email").asText(""));
            m.put("picture", n.path("picture").asText(""));
            return m;
        } catch (Exception e) {
            log.error("[Google-OAuth] fetchUser failed", e);
            throw new RuntimeException("Google 用户信息获取失败: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}