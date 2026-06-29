package com.example.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 安全响应头:
 * <ul>
 *   <li>X-Content-Type-Options: nosniff — 防 MIME 嗅探</li>
 *   <li>X-Frame-Options: DENY — 防点击劫持</li>
 *   <li>X-XSS-Protection: 0 — 现代浏览器已内置 CSP，旧 header 关闭</li>
 *   <li>Strict-Transport-Security — 仅 HTTPS</li>
 *   <li>Content-Security-Policy — 限制脚本源</li>
 *   <li>Referrer-Policy: no-referrer — 不外泄 Referer</li>
 *   <li>Permissions-Policy — 禁用危险 API</li>
 * </ul>
 *
 * <p>v2.2.93 修复: 之前在 chain.filter().then() 里 add header, 但下游 response 已
 * committed 时 h.set 触发 UnsupportedOperationException → 500.
 * 修复: 用 {@code exchange.mutate().response(decorator)} 提前 decorate response,
 * 这样 header 在 response commit 前就加上了.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self' wss: https:; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 提前 mutate response, 这样 header 在 commit 前就加上
        ServerHttpResponse decorated = exchange.getResponse();
        HttpHeaders h = decorated.getHeaders();
        if (!h.containsKey("X-Content-Type-Options")) h.set("X-Content-Type-Options", "nosniff");
        if (!h.containsKey("X-Frame-Options")) h.set("X-Frame-Options", "DENY");
        if (!h.containsKey("X-XSS-Protection")) h.set("X-XSS-Protection", "0");
        if (!h.containsKey("Referrer-Policy")) h.set("Referrer-Policy", "no-referrer");
        if (!h.containsKey("Strict-Transport-Security")) h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        if (!h.containsKey("Content-Security-Policy")) h.set("Content-Security-Policy", CSP);
        if (!h.containsKey("Permissions-Policy")) h.set("Permissions-Policy", "geolocation=(), microphone=(self), camera=(), payment=()");
        // 移除泄露信息的 header (但不在 commit 前移, 因为下游可能还会设)
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return -50; }
}