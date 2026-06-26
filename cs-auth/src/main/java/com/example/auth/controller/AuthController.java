package com.example.auth.controller;

import com.example.auth.service.AuthService;
import com.example.common.ApiException;
import com.example.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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

    // ============ 客户：静默 / OAuth ============
    @PostMapping("/silent-login")
    public ApiResponse<Map<String, Object>> silentLogin(@RequestBody(required = false) SilentReq req) {
        return ApiResponse.ok(authService.silentLogin(req == null ? null : req.getTempCode()));
    }

    @GetMapping("/wechat-oa/authorize")
    public void oaAuthorize(@RequestParam String redirectUri,
                            @RequestParam(required = false) String state,
                            HttpServletResponse resp) throws IOException {
        String s = state == null ? UUID.randomUUID().toString() : state;
        resp.sendRedirect(authService.oaAuthorizeUrl(redirectUri, s));
    }

    @GetMapping("/wechat-oa/callback")
    public void oaCallback(@RequestParam String code,
                           @RequestParam(required = false) String state,
                           HttpServletResponse resp) throws IOException {
        Map<String, Object> token = authService.oaCallback(code);
        resp.sendRedirect("/customer/?token=" + token.get("token"));
    }

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
        resp.sendRedirect("/agent/?token=" + token.get("token"));
    }

    // ============ 用户名 + 密码 ============
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterReq req) {
        if (req.getPassword() == null || req.getUsername() == null) {
            throw new ApiException(400, "用户名/密码必填");
        }
        return ApiResponse.ok(authService.register(req.getUsername(), req.getPassword(),
                req.getPhone(), req.getCode(), req.getNickname()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@RequestBody LoginReq req,
                                                              HttpServletRequest http) {
        return ApiResponse.ok(authService.loginByPassword(req.getUsername(), req.getPassword(),
                clientIp(http)));
    }

    // ============ 手机号 + 验证码 ============
    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSms(@RequestBody PhoneReq req) {
        return ApiResponse.ok(authService.sendSmsCode(req.getPhone()));
    }

    @PostMapping("/login-phone")
    public ApiResponse<Map<String, Object>> loginByPhone(@RequestBody PhoneLoginReq req) {
        return ApiResponse.ok(authService.loginByPhone(req.getPhone(), req.getCode()));
    }

    // ============ 管理员 ============
    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody LoginReq req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new ApiException(400, "账号/密码必填");
        }
        return ApiResponse.ok(authService.adminLogin(req.getUsername(), req.getPassword()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String auth) {
        return ApiResponse.ok(Map.of("token", auth.replace("Bearer ", "")));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0] : req.getRemoteAddr();
    }

    @Data public static class SilentReq { private String tempCode; }
    @Data public static class LoginReq { private String username; private String password; }
    @Data public static class PhoneReq { private String phone; }
    @Data public static class PhoneLoginReq { private String phone; private String code; }
    @Data public static class RegisterReq {
        private String username;
        private String password;
        private String phone;
        private String code;
        private String nickname;
    }
}