package com.example.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全上下文持有者：从 Gateway 透传的 Header 中读取
 */
public final class SecurityContextHolder {

    public static final String H_ROLE = "X-User-Role";
    public static final String H_USERID = "X-User-Id";
    public static final String H_NAME = "X-User-Name";
    public static final String H_CHANNEL = "X-User-Channel";
    public static final String H_SKILLS = "X-User-Skills";
    public static final String H_ADMIN = "X-Admin-Level";

    private SecurityContextHolder() {}

    public static SecurityContext current() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String role = req.getHeader(H_ROLE);
        if (role == null) return null;
        return new SecurityContext(
                role,
                req.getHeader(H_USERID),
                req.getHeader(H_NAME),
                req.getHeader(H_CHANNEL),
                req.getHeader(H_SKILLS),
                req.getHeader(H_ADMIN));
    }

    public static String requireUserId() {
        SecurityContext c = current();
        if (c == null || c.getUserId() == null) throw new ApiException(401, "未登录");
        return c.getUserId();
    }
    public static String requireRole(String... roles) {
        SecurityContext c = current();
        if (c == null) throw new ApiException(401, "未登录");
        for (String r : roles) if (r.equals(c.getRole())) return c.getUserId();
        throw new ApiException(403, "权限不足");
    }
}