package com.example.auth;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.example.auth.service.AuthService;
import com.example.auth.service.GithubOAuthClient;
import com.example.auth.service.GoogleOAuthClient;
import com.example.auth.service.OAuthStateCache;
import com.example.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth 第三方登录测试
 */
@SpringBootTest
class OAuthLoginTest {

    @Autowired AuthService authService;
    @Autowired GithubOAuthClient githubClient;
    @Autowired GoogleOAuthClient googleClient;
    @Autowired OAuthStateCache stateCache;
    @Autowired WechatUserRepo userRepo;

    @Test
    void githubAuthorizeUrl_containsState() {
        String url = authService.githubAuthorizeUrl("https://test.com/auth/github/callback", null);
        assertThat(url).contains("github.com/login/oauth/authorize");
        assertThat(url).contains("client_id=");
        assertThat(url).contains("state=");
    }

    @Test
    void googleAuthorizeUrl_containsState() {
        String url = authService.googleAuthorizeUrl("https://test.com/auth/google/callback", null);
        assertThat(url).contains("accounts.google.com");
        assertThat(url).contains("state=");
    }

    @Test
    void githubCallback_mockSuccess() {
        String url = authService.githubAuthorizeUrl("https://test.com/auth/github/callback", null);
        String state = extractState(url);

        Map<String, Object> result = authService.githubCallback("test-code-123", state);
        assertThat(result).containsKey("token");
        assertThat(result.get("customerId")).isNotNull();

        // 验证用户表创建
        WechatUser u = userRepo.findByProviderAndProviderUserId("GITHUB", "test-code-123").orElseThrow();
        assertThat(u.getProvider()).isEqualTo("GITHUB");
        assertThat(u.getCustomerId()).startsWith("gh-");
    }

    @Test
    void googleCallback_mockSuccess() {
        String url = authService.googleAuthorizeUrl("https://test.com/auth/google/callback", null);
        String state = extractState(url);

        Map<String, Object> result = authService.googleCallback("g-code-456", state, "https://test.com/auth/google/callback");
        assertThat(result).containsKey("token");

        WechatUser u = userRepo.findByProviderAndProviderUserId("GOOGLE", "google-g-code-456").orElseThrow();
        assertThat(u.getProvider()).isEqualTo("GOOGLE");
    }

    @Test
    void githubCallback_invalidState_rejected() {
        try {
            authService.githubCallback("test-code", "fake-state-12345");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
            assertThat(e.getMessage()).contains("state");
        }
    }

    @Test
    void googleCallback_invalidState_rejected() {
        try {
            authService.googleCallback("g-code", "fake-state-99999", "https://test.com/cb");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void oauthStateCache_oneTimeConsume() {
        String state = stateCache.generate("github");
        // 第一次消费：成功
        assertThat(stateCache.verifyAndConsume("github", state)).isTrue();
        // 第二次：失败（一次性）
        assertThat(stateCache.verifyAndConsume("github", state)).isFalse();
    }

    @Test
    void oauthStateCache_wrongProvider_fails() {
        String state = stateCache.generate("github");
        // 用 google 的 key 查 github 的 state → false
        assertThat(stateCache.verifyAndConsume("google", state)).isFalse();
        // 正确 key 仍可用
        assertThat(stateCache.verifyAndConsume("github", state)).isTrue();
    }

    @Test
    void sameUser_multipleLogins_returnsSameAccount() {
        String url1 = authService.githubAuthorizeUrl("https://test.com/cb", null);
        String state1 = extractState(url1);
        Map<String, Object> r1 = authService.githubCallback("same-code-789", state1);

        String url2 = authService.githubAuthorizeUrl("https://test.com/cb", null);
        String state2 = extractState(url2);
        Map<String, Object> r2 = authService.githubCallback("same-code-789", state2);

        // 同一 provider+providerUserId → 同一 customerId
        assertThat(r1.get("customerId")).isEqualTo(r2.get("customerId"));
    }

    private static String extractState(String url) {
        int idx = url.indexOf("state=");
        if (idx < 0) return null;
        String s = url.substring(idx + 6);
        int amp = s.indexOf('&');
        return amp < 0 ? s : s.substring(0, amp);
    }
}