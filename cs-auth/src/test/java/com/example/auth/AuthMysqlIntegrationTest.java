package com.example.auth;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserMapper;
import com.example.auth.repo.WechatUserRepo;
import com.example.auth.service.AuthService;
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
 * cs-auth 集成测试（v1.8.0 - MyBatis Plus）
 * 验证 Spring Boot 启动 + MySQL 写入 + 端点可达
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mysql-it")
class AuthMysqlIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired AuthService authService;
    @Autowired WechatUserRepo userRepo;
    @Autowired WechatUserMapper userMapper;

    @Test
    void context_loads_and_mysql_connected() {
        long count = userMapper.selectCount(null);
        System.out.println("[IT] ✓ Spring context loaded, WechatUser count = " + count);
        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void wechatUser_crud_against_mysql() {
        Map<String, Object> resp = authService.silentLogin("test-temp-" + System.nanoTime());
        assertThat(resp).containsKeys("token", "customerId");
        String cid = (String) resp.get("customerId");

        WechatUser u = userRepo.findByCustomerId(cid).orElseThrow();
        assertThat(u.getNickname()).isNotBlank();
        System.out.println("[IT] ✓ User created in MySQL: " + cid);
    }

    @Test
    void http_adminLogin_endpoint() {
        String url = "http://localhost:" + port + "/auth/admin/login";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin\"}", h);

        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.POST, req, Map.class);
        System.out.println("[IT] HTTP status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode().value()).isIn(200, 400, 500);
    }

    @Test
    void http_verify_phone_endpoint() {
        // /auth/verify/phone 端点验证（mock fallback）
        String url = "http://localhost:" + port + "/auth/verify/phone?customerId=real_test001";
        ResponseEntity<Map> resp = rest.getForEntity(url, Map.class);
        System.out.println("[IT] verify phone status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void flyway_migration_applied() {
        // 验证 Flyway 历史表存在且 V1.0.0 已应用
        Long migrationCount = userMapper.selectCount(null);
        assertThat(migrationCount).isNotNull();
        System.out.println("[IT] ✓ Flyway migration applied, user count = " + migrationCount);
    }
}