package com.example.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头（防 XSS / Clickjacking / MIME 嗅探 / Referer 泄漏）
 *
 * <ul>
 *   <li><b>X-Frame-Options: DENY</b> — 禁止 iframe 嵌入（防点击劫持）</li>
 *   <li><b>X-Content-Type-Options: nosniff</b> — 强制按 Content-Type 解析</li>
 *   <li><b>Strict-Transport-Security</b> — 强制 HTTPS（生产环境）</li>
 *   <li><b>Referrer-Policy: strict-origin-when-cross-origin</b> — 防 Referer 泄漏</li>
 *   <li><b>Permissions-Policy</b> — 禁用不必要的浏览器 API</li>
 *   <li><b>Content-Security-Policy</b> — 限制资源加载源</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) resp;
        // 防点击劫持
        response.setHeader("X-Frame-Options", "DENY");
        // 防 MIME 嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");
        // HSTS（生产强制 HTTPS）
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        // Referer 策略
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // 禁用危险 API
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        // XSS 保护（现代浏览器）
        response.setHeader("X-XSS-Protection", "1; mode=block");
        // CSP（生产应去掉 unsafe-inline，用 nonce）
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https: blob:; " +
            "font-src 'self' data:; " +
            "connect-src 'self' ws: wss: https:; " +
            "frame-ancestors 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'");

        chain.doFilter(req, resp);
    }
}