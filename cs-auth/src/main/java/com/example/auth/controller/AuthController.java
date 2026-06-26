package com.example.auth.controller;

import com.example.auth.service.AuthService;
import com.example.common.ApiException;
import com.example.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 认证入口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ============ 客户静默登录 ============
    /** 公众号 H5 静默登录：前端用临时 code 调用 */
    @PostMapping("/silent-login")
    public ApiResponse<Map<String, Object>> silentLogin(@RequestBody(required = false) SilentReq req) {
        return ApiResponse.ok(authService.silentLogin(req == null ? null : req.getTempCode()));
    }

    // ============ 公众号 OAuth ============
    /** 第一步：跳转到微信授权页 */
    @GetMapping("/wechat-oa/authorize")
    public void oaAuthorize(@RequestParam String redirectUri,
                            @RequestParam(required = false) String state,
                            HttpServletResponse resp) throws IOException {
        String s = state == null ? UUID.randomUUID().toString() : state;
        resp.sendRedirect(authService.oaAuthorizeUrl(redirectUri, s));
    }

    /** 第二步：微信回调（带 code + state） */
    @GetMapping("/wechat-oa/callback")
    public void oaCallback(@RequestParam String code,
                           @RequestParam(required = false) String state,
                           HttpServletResponse resp) throws IOException {
        Map<String, Object> token = authService.oaCallback(code);
        // 简化：把 token 回跳到前端（生产建议用一次性 code 交换）
        String tokenStr = (String) token.get("token");
        String redirect = "/customer/?token=" + tokenStr;
        resp.sendRedirect(redirect);
    }

    // ============ 企微 OAuth ============
    @GetMapping("/wechat-work/authorize")
    public void workAuthorize(@RequestParam String redirectUri,
                              @RequestParam(required = false) String state,
                              HttpServletResponse resp) throws IOException {
        String s = state == null ? UUID.randomUUID().toString() : state;
        resp.sendRedirect(authService.workAuthorizeUrl(redirectUri, s));
    }

    @GetMapping("/wechat-work/callback")
    public void workCallback(@RequestParam String code,
                             @RequestParam(required = false) String state,
                             HttpServletResponse resp) throws IOException {
        Map<String, Object> token = authService.workCallback(code);
        String tokenStr = (String) token.get("token");
        resp.sendRedirect("/agent/?token=" + tokenStr);
    }

    // ============ 管理员 ============
    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody LoginReq req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new ApiException(400, "账号/密码必填");
        }
        return ApiResponse.ok(authService.adminLogin(req.getUsername(), req.getPassword()));
    }

    // ============ Token 刷新 ============
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String auth) {
        // 简化：重新发同 token；生产做 refreshToken
        return ApiResponse.ok(Map.of("token", auth.replace("Bearer ", "")));
    }

    @Data
    public static class SilentReq { private String tempCode; }
    @Data
    public static class LoginReq { private String username; private String password; }
}