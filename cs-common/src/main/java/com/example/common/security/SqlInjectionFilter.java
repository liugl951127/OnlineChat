package com.example.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * SQL 注入 / XSS 兜底过滤
 *
 * <p>对所有 query / form 参数做危险字符检测，遇到直接拒绝。
 * MyBatis 已使用参数化查询，此 Filter 是最后一道防线。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SqlInjectionFilter implements Filter {

    /** SQL 注入特征（仅检测最明显的攻击载荷） */
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        Pattern.compile("(?i).*\\bunion\\s+select\\b.*"),
        Pattern.compile("(?i).*\\bdrop\\s+(table|database)\\b.*"),
        Pattern.compile("(?i).*\\binsert\\s+into\\b.*"),
        Pattern.compile("(?i).*\\bdelete\\s+from\\b.*"),
        Pattern.compile("(?i).*\\bor\\s+['\"]?1['\"]?\\s*=\\s*['\"]?1['\"]?.*"),
        Pattern.compile("(?i).*--.*"),
        Pattern.compile("(?i).*;\\s*(drop|delete|update|insert)\\b.*"),
        Pattern.compile("(?i).*xp_cmdshell.*"),
        Pattern.compile("(?i).*\\bsleep\\s*\\(\\s*\\d+\\s*\\).*"),
        Pattern.compile("(?i).*benchmark\\s*\\(.*")
    };

    /** XSS 特征 */
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("(?i).*<\\s*script.*>.*"),
        Pattern.compile("(?i).*javascript\\s*:.*"),
        Pattern.compile("(?i).*on\\w+\\s*=\\s*['\"].*"),
        Pattern.compile("(?i).*<\\s*iframe.*>.*"),
        Pattern.compile("(?i).*eval\\s*\\(.*"),
        Pattern.compile("(?i).*expression\\s*\\(.*")
    };

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        SqlHttpServletRequestWrapper wrapped = new SqlHttpServletRequestWrapper((HttpServletRequest) req);
        try {
            // 检测所有 query 参数
            for (String value : wrapped.getParameterMap().values().stream().flatMap(java.util.Arrays::stream).toList()) {
                if (containsDangerous(value)) {
                    reject((HttpServletResponse) resp, "检测到非法字符");
                    return;
                }
            }
            chain.doFilter(wrapped, resp);
        } catch (IllegalArgumentException e) {
            reject((HttpServletResponse) resp, e.getMessage());
        }
    }

    private boolean containsDangerous(String value) {
        if (value == null) return false;
        for (Pattern p : SQL_INJECTION_PATTERNS) if (p.matcher(value).matches()) return true;
        for (Pattern p : XSS_PATTERNS) if (p.matcher(value).matches()) return true;
        return false;
    }

    private void reject(HttpServletResponse resp, String msg) throws IOException {
        log.warn("[SQL/XSS] rejected: {}", msg);
        resp.setStatus(400);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":400,\"msg\":\"" + msg + "\"}");
    }

    /** 包装请求，调用 getParameter 时做检测 */
    static class SqlHttpServletRequestWrapper extends HttpServletRequestWrapper {
        public SqlHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            check(value);
            return value;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values != null) for (String v : values) check(v);
            return values;
        }

        private void check(String value) {
            if (value == null) return;
            for (Pattern p : SQL_INJECTION_PATTERNS) {
                if (p.matcher(value).matches()) {
                    throw new IllegalArgumentException("SQL 注入特征: " + value);
                }
            }
        }
    }
}