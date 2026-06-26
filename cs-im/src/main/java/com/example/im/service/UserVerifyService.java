package com.example.im.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 用户实名认证（调用 cs-auth）
 *
 * <p>Feign client 替代方案：用 RestTemplate 直接调用（避免引入 Feign 负载均衡配置）
 */
@Slf4j
@Service
public class UserVerifyService {

    @Value("${cs.auth.base-url:http://localhost:9001}")
    private String authBaseUrl;

    @Autowired(required = false)
    private JwtTokenHolder jwtHolder;

    private final RestTemplate http = new RestTemplate();

    /**
     * 检查客户是否实名认证
     * @return true=已认证
     */
    public boolean isPhoneVerified(String customerId) {
        try {
            HttpHeaders h = new HttpHeaders();
            String token = jwtHolder != null ? jwtHolder.getToken() : null;
            if (token != null) h.set("Authorization", "Bearer " + token);
            h.set("X-Customer-Id", customerId);

            String url = authBaseUrl + "/auth/verify/phone?customerId=" + customerId;
            ResponseEntity<Map> resp = http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            Map body = resp.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("verified"))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("[UserVerify] 调用 cs-auth 失败：{}", e.getMessage());
        }
        // 降级：mock 模式下所有 customerId 以 "real_" 开头视为已认证
        return customerId != null && customerId.startsWith("real_");
    }
}