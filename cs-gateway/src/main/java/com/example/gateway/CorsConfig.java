package com.example.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * cs-gateway CORS 全局配置（v2.2.35）。
 *
 * <p>Spring Cloud Gateway (WebFlux) 用 CorsWebFilter，不是传统 CorsFilter。
 *
 * <p>支持：
 * <ul>
 *   <li>所有来源（*）—— 适合公网客服场景；生产可收敛到具体域名</li>
 *   <li>所有方法（GET/POST/PUT/DELETE/OPTIONS）</li>
 *   <li>所有头（包括自定义 X-User-Role）</li>
 *   <li>Credentials (cookie / auth header)</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 公网场景：允许所有 origin；生产可改为具体域名列表
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "Authorization", "X-CSRF-Token", "X-User-Role", "X-User-Id",
                "Content-Disposition", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);  // 1h preflight 缓存

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * 兜底 OPTIONS 处理（v2.2.35）。
     *
     * <p>Spring Cloud Gateway 在某些情况下不返回正确的 CORS 头（特别是带 Origin 但路由不匹配）。
     * 这里强制拦截 OPTIONS 直接返 200 + CORS 头。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter corsFallbackFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest req = exchange.getRequest();
            ServerHttpResponse resp = exchange.getResponse();
            if (HttpMethod.OPTIONS.equals(req.getMethod())) {
                String origin = req.getHeaders().getOrigin();
                if (origin != null) {
                    resp.getHeaders().add("Access-Control-Allow-Origin", origin);
                    resp.getHeaders().add("Access-Control-Allow-Methods",
                            "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                    resp.getHeaders().add("Access-Control-Allow-Headers", "*");
                    resp.getHeaders().add("Access-Control-Allow-Credentials", "true");
                    resp.getHeaders().add("Access-Control-Max-Age", "3600");
                    resp.setStatusCode(HttpStatus.OK);
                    return resp.setComplete();
                }
            }
            return chain.filter(exchange);
        };
    }
}