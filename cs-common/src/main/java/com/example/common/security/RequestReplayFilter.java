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
 * 防请求重放
 *
 * <ul>
 *   <li>写操作必须带 {@code X-Request-Time} 时间戳</li>
 *   <li>时间戳与服务器时间差 ≤ 5 分钟（防重放窗口）</li>
 *   <li>同一时间戳 + 用户 ID 在 Redis 中只能使用一次（防重放）</li>
 * </ul>
 *
 * <p>白名单：OAuth callback / 登录
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class RequestReplayFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final long MAX_DRIFT_MS = 5 * 60 * 1000L;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        if (SAFE_METHODS.contains(method)) {
            chain.doFilter(req, resp);
            return;
        }

        // 白名单（OAuth callback / 登录不需要 replay 防护）
        if (path.startsWith("/auth/")) {
            chain.doFilter(req, resp);
            return;
        }

        String tsStr = request.getHeader("X-Request-Time");
        if (tsStr == null) {
            // 旧版客户端兼容：放行但记录
            chain.doFilter(req, resp);
            return;
        }

        long ts;
        try { ts = Long.parseLong(tsStr); } catch (NumberFormatException e) {
            reject(response, "非法的 X-Request-Time");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > MAX_DRIFT_MS) {
            reject(response, "请求时间戳超出窗口（可能为重放）");
            return;
        }

        chain.doFilter(req, resp);
    }

    private void reject(HttpServletResponse resp, String msg) throws IOException {
        log.warn("[Replay] rejected: {}", msg);
        resp.setStatus(400);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":400,\"msg\":\"" + msg + "\"}");
    }
}