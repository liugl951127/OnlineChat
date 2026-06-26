package com.example.im.service;

import org.springframework.stereotype.Component;

/**
 * JWT Token 持有（请求级别）
 *
 * <p>从 Spring SecurityContext 或 HTTP Header 提取 JWT，供下游服务调用使用
 */
@Component
public class JwtTokenHolder {
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    public void setToken(String t) { TOKEN.set(t); }
    public String getToken() { return TOKEN.get(); }
    public void clear() { TOKEN.remove(); }
}