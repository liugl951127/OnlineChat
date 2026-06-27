package com.example.gateway;

import com.example.common.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 全局过滤器：
 * <ul>
 *   <li>白名单路径放行（OAuth 回调 / 登录 / 健康检查）</li>
 *   <li>其他路径：解析 JWT，把身份写到 Request Header 透传给下游</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;

    @Value("${cs.gateway.jwt.secret:default-jwt-secret-change-me-in-production-32-chars-min}")
    private String secret;

    private static final List<String> WHITE_LIST = List.of(
            // ============ 微信授权流程（点公众号/企微按钮 → 后端返 URL → 跳微信） ============
            "/auth/wechat-oa/authorize",
            "/auth/wechat-oa/authorize-json",
            "/auth/wechat-oa/callback",
            "/auth/wechat-oa/callback-json",
            "/auth/wx-oa/h5-entry",
            "/auth/wx-mini/login",
            "/auth/wechat-work/authorize",
            "/auth/wechat-work/authorize-json",
            "/auth/wechat-work/callback",
            "/auth/wechat-work/callback-json",
            // ============ GitHub / Google OAuth ============
            "/auth/github/authorize",
            "/auth/github/authorize-json",
            "/auth/github/callback",
            "/auth/github/callback-json",
            "/auth/google/authorize",
            "/auth/google/authorize-json",
            "/auth/google/callback",
            "/auth/google/callback-json",
            // ============ 账号密码登录 ============
            "/auth/login",
            "/auth/login-phone",
            "/auth/register",
            "/auth/sms/send",
            "/auth/silent-login",
            "/auth/agent/login",
            "/auth/admin/login",
            "/auth/refresh",
            // ============ 验证码 / KYC / 验证 ============
            "/auth/verify/phone",
            "/auth/wechat-oa/js-sign",
            "/auth/wechat-oa/customer-message",
            "/auth/wechat-oa/template-send",
            "/auth/wx-mini/subscribe-send",
            // ============ 健康检查 ============
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();

        // OPTIONS 放行（CORS）
        if ("OPTIONS".equalsIgnoreCase(req.getMethod().name())) return chain.filter(exchange);

        // 健康检查（K8s 探针）一律放行
        if (path.startsWith("/actuator")) return chain.filter(exchange);

        // 白名单：精确匹配（开头完全一致）
        for (String w : WHITE_LIST) {
            if (path.equals(w) || path.startsWith(w + "/") || path.startsWith(w + "?")) {
                return chain.filter(exchange);
            }
        }

        String token = extractToken(req);
        if (token == null) {
            // WebSocket / SockJS 握手也可以走 query 传 token
            if (path.contains("/ws")) {
                token = req.getQueryParams().getFirst("token");
            }
        }
        if (token == null) {
            log.debug("[Gateway] no token for {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Claims claims = jwtUtils.parse(token);
        if (claims == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest mutated = req.mutate()
                .header("X-User-Role", str(claims.get("role")))
                .header("X-User-Id", str(claims.get("userId")))
                .header("X-User-Name", str(claims.get("displayName")))
                .header("X-User-Channel", str(claims.get("channel")))
                .header("X-User-Skills", str(claims.get("skills")))
                .header("X-Admin-Level", str(claims.get("adminLevel")))
                .header("X-Trace-Id", java.util.UUID.randomUUID().toString())
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String extractToken(ServerHttpRequest req) {
        String h = req.getHeaders().getFirst("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        return null;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    @Override
    public int getOrder() { return -100; }
}