package com.example.auth.controller;

import com.example.auth.service.AuthService;
import com.example.auth.service.TokenBlacklistService;
import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.CryptoUtils;
import com.example.common.security.CsrfTokenIssuer;
import com.example.auth.repo.WechatUserRepo;
import com.example.auth.domain.WechatUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * v2.3.0 认证入口 (简化版)
 *
 * <p>原则: 只支持账号+密码登录, 按 username 前缀区分角色:
 * <ul>
 *   <li>用户名以 "admin_" 开头 或 username == "admin" → 管理员 (从 user 表查 role)</li>
 *   <li>用户表里有 username 字段 + password_hash → 客户 OR 坐席 (按 user 表里 customerId 的 a-/c- 前缀)</li>
 * </ul>
 *
 * <p>端点:
 * <pre>
 *   POST /auth/login        客户/坐席/管理员 通用登录 (按 username 查 user 表)
 *   POST /auth/refresh      续 token
 *   POST /auth/logout       登出 (拉黑 token)
 *   GET  /auth/me           当前登录用户的信息 (验证 token 还活着)
 *   POST /auth/register     客户注册 (可选)
 *   POST /auth/password     修改密码 (登录后)
 * </pre>
 *
 * <p>删除: 静默登录 / 手机号 / 验证码 / 公众号 / 企微 / GitHub / Google / WX_MINI / admin/login / agent/login / 管理员硬编码
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenIssuer csrfIssuer;
    private final TokenBlacklistService blacklistService;
    private final WechatUserRepo userRepo;

    /**
     * 通用登录: 客户/坐席/管理员
     *
     * <p>简化策略: 后端按 username 查 user 表, 返回 token + role, 前端按 role 路由.
     * 管理员 username='admin' 走 AuthService.adminLogin() (硬编码兼容, 不是用户表里).
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> req,
                                                    HttpServletRequest http,
                                                    HttpServletResponse resp) {
        String username = str(req.get("username"));
        String password = str(req.get("password"));
        if (username == null || password == null) {
            throw new ApiException(400, "账号/密码必填");
        }
        String clientIp = clientIp(http);

        Map<String, Object> body;
        String userTrim = username.trim();
        if ("admin".equals(userTrim) || userTrim.startsWith("admin_")) {
            body = new java.util.HashMap<>(authService.adminLogin(userTrim, password));
        } else {
            body = new java.util.HashMap<>(authService.unifiedPasswordLogin(userTrim, password, clientIp));
        }
        body.put("csrf", csrfIssuer.issue(resp));

        log.info("[Auth] login OK user={} role={} ip={}", userTrim, body.get("role"), clientIp);
        return ApiResponse.ok(body);
    }

    /**
     * 注册 (可选, 留着便于内部增账号)
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> req) {
        String username = str(req.get("username"));
        String password = str(req.get("password"));
        if (username == null || password == null) {
            throw new ApiException(400, "用户名/密码必填");
        }
        validatePasswordStrength(password);
        return ApiResponse.ok(authService.register(username, password,
                str(req.get("phone")), str(req.get("code")), str(req.get("nickname"))));
    }

    /**
     * 刷新 token (v2.3.0: 简单续期 + 检查黑名单)
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String auth) {
        String oldToken = auth == null ? "" : auth.replace("Bearer ", "").trim();
        if (oldToken.isEmpty()) throw new ApiException(401, "需要 Authorization");
        // 简单实现: 同 token 返回, 但加 blacklisted check
        if (blacklistService.isRevoked(oldToken)) {
            throw new ApiException(401, "token 已失效, 请重新登录");
        }
        return ApiResponse.ok(Map.of("token", oldToken));
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        String token = auth == null ? "" : auth.replace("Bearer ", "").trim();
        if (!token.isEmpty()) {
            blacklistService.revoke(token, 86400);
        }
        return ApiResponse.ok(Map.of("msg", "logged out"));
    }

    /**
     * 当前登录用户信息 (前端 App.vue 启动时调)
     * 从 gateway 转过来的 X-User-Id 等 header 读
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest req) {
        // cs-gateway 已把 JWT 解到 header, 直接读
        Map<String, String> h = new java.util.HashMap<>();
        java.util.Collections.list(req.getHeaderNames()).forEach(n -> h.put(n, req.getHeader(n)));
        return ApiResponse.ok(Map.of(
                "userId",   h.getOrDefault("X-User-Id", ""),
                "role",     h.getOrDefault("X-User-Role", ""),
                "channel",  h.getOrDefault("X-User-Channel", ""),
                "adminLevel", h.getOrDefault("X-Admin-Level", "")
        ));
    }

    /**
     * 修改自己密码 (登录后)
     */
    @PostMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> req,
                                                            HttpServletRequest httpReq) {
        String cid = httpReq.getHeader("X-User-Id");
        if (cid == null || cid.isBlank()) throw new ApiException(401, "未登录");
        WechatUser u = userRepo.findByCustomerId(cid)
                .orElseThrow(() -> new ApiException(404, "用户不存在"));
        String oldPwd = str(req.get("oldPassword"));
        String newPwd = str(req.get("newPassword"));
        if (!CryptoUtils.verifyPassword(oldPwd, u.getPasswordHash())) {
            throw new ApiException(400, "旧密码错误");
        }
        validatePasswordStrength(newPwd);
        u.setPasswordHash(CryptoUtils.hashPassword(newPwd));
        userRepo.save(u);
        blacklistService.bumpUserVersion(cid);
        return ApiResponse.ok(Map.of("msg", "密码已修改, 请重新登录"));
    }

    /**
     * 校验密码强度 (>=6 位)
     */
    private void validatePasswordStrength(String pwd) {
        if (pwd == null || pwd.length() < 6) {
            throw new ApiException(400, "密码至少 6 位");
        }
        if (pwd.length() > 64) {
            throw new ApiException(400, "密码最长 64 位");
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 提取客户端 IP (proxy 头优先)
     */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isEmpty()) return real;
        return req.getRemoteAddr();
    }
}
