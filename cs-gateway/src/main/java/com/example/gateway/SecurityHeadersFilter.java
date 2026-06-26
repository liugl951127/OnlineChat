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
 * 安全响应头：
 * <ul>
 *   <li>X-Content-Type-Options: nosniff — 防 MIME 嗅探</li>
 *   <li>X-Frame-Options: DENY — 防点击劫持</li>
 *   <li>X-XSS-Protection: 0 — 现代浏览器已内置 CSP，旧 header 关闭</li>
 *   <li>Strict-Transport-Security — 仅 HTTPS</li>
 *   <li>Content-Security-Policy — 限制脚本源</li>
 *   <li>Referrer-Policy: no-referrer — 不外泄 Referer</li>
 *   <li>Permissions-Policy — 禁用危险 API</li>
 * </ul>
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
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse resp = exchange.getResponse();
            HttpHeaders h = resp.getHeaders();
            h.set("X-Content-Type-Options", "nosniff");
            h.set("X-Frame-Options", "DENY");
            h.set("X-XSS-Protection", "0");
            h.set("Referrer-Policy", "no-referrer");
            h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            h.set("Content-Security-Policy", CSP);
            h.set("Permissions-Policy", "geolocation=(), microphone=(self), camera=(), payment=()");
            // 移除泄露信息的 header
            if (h.containsKey("Server")) h.remove("Server");
            if (h.containsKey("X-Powered-By")) h.remove("X-Powered-By");
        }));
    }

    @Override
    public int getOrder() { return -50; }
}