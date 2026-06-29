package com.example.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF 防护：双提交 Cookie + Header 校验
 *
 * <p>写操作（POST/PUT/DELETE/PATCH）必须同时满足：
 * <ol>
 *   <li>请求头 {@code X-CSRF-Token} 与 Cookie {@code CSRF-TOKEN} 一致</li>
 *   <li>Token 长度 ≥ 32</li>
 *   <li>Referer 与 Host 同源（防跨站）</li>
 * </ol>
 *
 * <p>白名单：OAuth 回调、文件上传、登录（登录时下发 CSRF Token）
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CsrfFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> WHITELIST_PATHS = Set.of(
        "/auth/login", "/auth/login-phone", "/auth/register",
        "/auth/admin/login", "/auth/agent/login",
        // v2.2.85: 实际路径是 silent-login (不是 silent)
        "/auth/silent-login",
        "/auth/sms/send", "/auth/refresh", "/auth/logout",
        "/auth/admin/revoke-user", "/auth/admin/reset-agent-password",
        "/auth/wx-mini/login", "/auth/wx-oa/h5-entry",
        "/auth/wechat-oa/callback", "/auth/wechat-oa/callback-json",
        "/auth/wechat-work/callback", "/auth/github/callback",
        "/auth/google/callback",
        // v2.2.80: subscribe-check + qrcode-for-subscribe
        "/auth/wechat-oa/subscribe-check", "/auth/wechat-oa/qrcode-for-subscribe",
        // v2.2.28: 微信推送端点 (微信服务器 POST, 无 CSRF)
        "/auth/wechat-oa/customer-message", "/auth/wechat-oa/template-send",
        "/auth/wx-mini/subscribe-send",
        // v2.2.97: 录像上传 (客户 SDK 不带 CSRF cookie; 防护靠 JWT + 同源 referer + session 归属验证)
        "/im/monitor/upload", "/im/monitor/end",
        // v2.3.0: 多端点 WS (SockJS 握手 GET 透传)
        "/ws", "/ws/customer", "/ws/agent", "/ws/admin"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        if (SAFE_METHODS.contains(method) || WHITELIST_PATHS.contains(path)) {
            chain.doFilter(req, resp);
            return;
        }

        // 1. Referer 校验（同源）
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                java.net.URI r = java.net.URI.create(referer);
                String host = request.getHeader("Host");
                if (host != null && !r.getHost().equals(host.split(":")[0])) {
                    reject(response, "跨站请求被拒绝（Referer 与 Host 不匹配）");
                    return;
                }
            } catch (Exception ignored) {}
        }

        // 2. CSRF Token 校验
        String headerToken = request.getHeader("X-CSRF-Token");
        String cookieToken = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("CSRF-TOKEN".equals(c.getName())) { cookieToken = c.getValue(); break; }
            }
        }

        if (headerToken == null || headerToken.length() < 32) {
            reject(response, "缺少或非法的 CSRF Token");
            return;
        }
        if (cookieToken == null || !constantTimeEquals(headerToken, cookieToken)) {
            reject(response, "CSRF Token 校验失败");
            return;
        }

        chain.doFilter(req, resp);
    }

    private void reject(HttpServletResponse resp, String msg) throws IOException {
        log.warn("[CSRF] rejected: {}", msg);
        resp.setStatus(403);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":403,\"msg\":\"" + msg + "\"}");
    }

    /** 常量时间比较（防计时攻击） */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}