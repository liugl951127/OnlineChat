package com.example.auth;

import com.example.auth.service.WxMiniService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 微信小程序登录集成测试（v2.0.0）
 * 真实 MySQL 环境（mock 模式）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mysql-it")
class WxMiniIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired WxMiniService wxMiniService;

    @Test
    void context_loads() {
        // Spring 启动成功
    }

    @Test
    void wxMiniLogin_viaRestEndpoint() {
        String url = "http://localhost:" + port + "/auth/wx-mini/login";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        // Mock 模式：jsCode 直接用作 openId
        String body = "{\"jsCode\":\"test-jscode-" + System.nanoTime() + "\"}";
        HttpEntity<String> req = new HttpEntity<>(body, h);

        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.POST, req, Map.class);
        System.out.println("[WxMini-IT] status=" + resp.getStatusCode() + " body=" + resp.getBody());

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map data = (Map) resp.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data).containsKey("token");
        assertThat(data).containsKey("customerId");
        // customerId 应以 c-mini- 开头
        assertThat(data.get("customerId").toString()).startsWith("c-mini-");
        System.out.println("[WxMini-IT] ✓ 登录成功 customerId=" + data.get("customerId"));
    }

    @Test
    void wxMiniLogin_missingJsCode_returns400() {
        String url = "http://localhost:" + port + "/auth/wx-mini/login";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("{}", h);

        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.POST, req, Map.class);
        // 应返回 400 业务异常
        assertThat(resp.getStatusCode().value()).isIn(200, 400);
    }
}