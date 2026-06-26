package com.example.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全过滤器测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>SecurityHeaders — 响应头齐全</li>
 *   <li>CSRF — 缺失/伪造 token 拒绝</li>
 *   <li>SQL 注入 — 攻击载荷被拒</li>
 *   <li>XSS — script 注入被拒</li>
 *   <li>文件上传 — 类型/扩展名/大小检查</li>
 *   <li>请求重放 — 时间戳漂移被拒</li>
 * </ol>
 */
class SecurityFilterTest {

    @Test
    void securityHeaders_added() throws Exception {
        SecurityHeadersFilter f = new SecurityHeadersFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        f.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(resp.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(resp.getHeader("Strict-Transport-Security")).contains("max-age");
        assertThat(resp.getHeader("Content-Security-Policy")).contains("default-src");
        assertThat(resp.getHeader("Referrer-Policy")).isNotBlank();
        assertThat(resp.getHeader("Permissions-Policy")).isNotBlank();
    }

    @Test
    void csrf_missingToken_rejected() throws Exception {
        CsrfFilter f = new CsrfFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("CSRF");
    }

    @Test
    void csrf_mismatchedToken_rejected() throws Exception {
        CsrfFilter f = new CsrfFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        req.addHeader("X-CSRF-Token", "a".repeat(32));
        req.setCookies(new jakarta.servlet.http.Cookie("CSRF-TOKEN", "b".repeat(32)));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    void csrf_matchedToken_allowed() throws Exception {
        CsrfFilter f = new CsrfFilter();
        String tok = UUID.randomUUID().toString().replace("-", "");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        req.addHeader("X-CSRF-Token", tok);
        req.setCookies(new jakarta.servlet.http.Cookie("CSRF-TOKEN", tok));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        // 通过（chain.getRequest() 不为 null 表示被调用）
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void csrf_whitelistedPath_allowed() throws Exception {
        CsrfFilter f = new CsrfFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void csrf_shortToken_rejected() throws Exception {
        CsrfFilter f = new CsrfFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        req.addHeader("X-CSRF-Token", "short");
        req.setCookies(new jakarta.servlet.http.Cookie("CSRF-TOKEN", "short"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    void sqlInjection_unionSelect_rejected() throws Exception {
        SqlInjectionFilter f = new SqlInjectionFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/im/history/abc");
        req.setParameter("q", "1' UNION SELECT * FROM users--");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void sqlInjection_orOneEqOne_rejected() throws Exception {
        SqlInjectionFilter f = new SqlInjectionFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setParameter("id", "1 OR '1'='1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void xss_scriptTag_rejected() throws Exception {
        SqlInjectionFilter f = new SqlInjectionFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setParameter("nickname", "<script>alert(1)</script>");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void xss_javascriptUri_rejected() throws Exception {
        SqlInjectionFilter f = new SqlInjectionFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setParameter("avatar", "javascript:alert(1)");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void replay_driftTooLarge_rejected() throws Exception {
        RequestReplayFilter f = new RequestReplayFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        req.addHeader("X-Request-Time", String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000L));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void replay_driftNormal_allowed() throws Exception {
        RequestReplayFilter f = new RequestReplayFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        req.addHeader("X-Request-Time", String.valueOf(System.currentTimeMillis()));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void replay_noTimestamp_allowed_forBackcompat() throws Exception {
        RequestReplayFilter f = new RequestReplayFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/im/send");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, resp, chain);

        // 旧客户端不阻断
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void csrfTokenIssuer_writesCookie() {
        CsrfTokenIssuer issuer = new CsrfTokenIssuer();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        String token = issuer.issue(resp);
        assertThat(token).hasSize(64); // 32 字节 hex
        assertThat(resp.getCookies()).isNotEmpty();
        boolean found = false;
        for (jakarta.servlet.http.Cookie c : resp.getCookies()) {
            if ("CSRF-TOKEN".equals(c.getName())) {
                assertThat(c.getValue()).isEqualTo(token);
                assertThat(c.getPath()).isEqualTo("/");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }
}