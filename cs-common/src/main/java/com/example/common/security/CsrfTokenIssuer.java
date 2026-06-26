package com.example.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * CSRF Token 下发：登录成功后写入 Cookie + 返回 header
 *
 * <p>前端从响应头 X-CSRF-Token 读取并存储到 localStorage，
 * 后续写操作带 X-CSRF-Token 头，Filter 校验与 Cookie 一致。
 */
@Component
public class CsrfTokenIssuer {

    private static final SecureRandom RNG = new SecureRandom();

    public String issue(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String token = java.util.HexFormat.of().formatHex(bytes);

        // 写 Cookie（同源 + HttpOnly=false 以便 JS 读取双提交校验）
        Cookie cookie = new Cookie("CSRF-TOKEN", token);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        cookie.setHttpOnly(false);  // 允许前端读取，与 Header 双提交
        // 生产环境应加 secure=true（强制 HTTPS）
        cookie.setSecure(false);    // 开发环境
        response.addCookie(cookie);

        return token;
    }
}