package com.example.auth;

import com.example.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthEndToEndTest {

    @Autowired AuthService authService;

    @Test
    void silentLogin_returnsToken() {
        Map<String, Object> resp = authService.silentLogin("test-temp-code");
        assertThat(resp).containsKeys("token", "customerId", "channel");
        assertThat(((String) resp.get("channel"))).isEqualTo("OA");
    }

    @Test
    void adminLogin_returnsAdminToken() {
        Map<String, Object> resp = authService.adminLogin("admin", "admin");
        assertThat(resp).containsKeys("token");
        assertThat(resp.get("role")).isEqualTo("ADMIN");
    }

    @Test
    void adminLogin_wrongPassword_throws() {
        try {
            authService.adminLogin("admin", "wrong");
            org.junit.jupiter.api.Assertions.fail("应失败");
        } catch (com.example.common.ApiException e) {
            assertThat(e.getCode()).isEqualTo(401);
        }
    }

    @Test
    void oaCallback_createsNewUser() {
        Map<String, Object> resp = authService.oaCallback("test-code-" + System.nanoTime());
        assertThat(resp).containsKeys("token", "customerId");
    }
}