package com.example.auth.controller;

import com.example.auth.service.AuthService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.WechatJsSignService;
import com.example.auth.service.WechatPushService;
import com.example.auth.service.WxMiniService;
import com.example.auth.service.WxMiniSubscribeService;
import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.CryptoUtils;
import com.example.common.security.CsrfTokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证入口
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CsrfTokenIssuer csrfIssuer;
    private final WxMiniService wxMiniService;
    private final WechatJsSignService jsSignService;
    private final WechatPushService pushService;
    private final WxMiniSubscribeService miniSubscribeService;
    private final TokenBlacklistService blacklistService;
    private final com.example.common.JwtUtils jwtUtils;
    private final WechatUserRepo userRepo;

    // ============ 客户：静默 / OAuth ============
    @PostMapping("/silent-login")
    public ApiResponse<Map<String, Object>> silentLogin(@RequestBody(required = false) SilentReq req) {
        // v2.2.75: SilentReq.deviceId (不是 tempCode)
        return ApiResponse.ok(authService.silentLogin(req == null ? null : req.getDeviceId()));
    }

    @GetMapping("/wechat-oa/authorize")
    public void oaAuthorize(@RequestParam(name = "redirect_uri", required = false) String redirectUri,
                            @RequestParam(required = false) String state,
                            @RequestParam(required = false) String scope,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            HttpServletRequest request,
                            HttpServletResponse resp) throws IOException {
        // 开箱即用：如果前端没传 redirectUri，依次 fallback：
        // 1. 请求参数 redirectUri
        // 2. Referer header (从哪个页面点过来)
        // 3. 从 Origin 或 X-Forwarded-Host 拼出绝对 URL
        // 4. 默认使用 host + /auth/wechat-oa/callback-json (后端 API, SPA 后续轮询拿 token)
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/wechat-oa/callback-json");
        String s = state == null ? UUID.randomUUID().toString() : state;
        // v2.2.38: 默认 snsapi_userinfo (公众号 PC 端 snsapi_base 已废弃)
        String sc = (scope == null || scope.isBlank()) ? "snsapi_userinfo" : scope;
        log.info("[OAuth-Authorize] wechat-oa redirectUri={} scope={} state={}", finalRedirectUri, sc, s);
        resp.sendRedirect(authService.oaAuthorizeUrl(finalRedirectUri, s, sc));
    }

    /**
     * 返回 JSON 版本的 authorize URL (供 axios 调用，避开 location.href 重定向问题)
     *
     * <p>返回：{ url: "https://open.weixin.qq.com/connect/oauth2/authorize?..." }
     * <p>前端拿 url 后 window.location.href = url 跳转。
     */
    @GetMapping("/wechat-oa/authorize-json")
    public ApiResponse<Map<String, String>> oaAuthorizeJson(
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpServletRequest request) {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/wechat-oa/callback-json");
        String s = state == null ? UUID.randomUUID().toString() : state;
        // v2.2.38: 公众号默认 snsapi_userinfo（snsapi_base 已废弃）
        String sc = (scope == null || scope.isBlank()) ? "snsapi_userinfo" : scope;
        String url = authService.oaAuthorizeUrl(finalRedirectUri, s, sc);
        return ApiResponse.ok(Map.of("url", url, "state", s, "scope", sc));
    }

    @GetMapping("/wechat-oa/callback")
    public void oaCallback(@RequestParam String code,
                           @RequestParam(required = false) String state,
                           HttpServletResponse resp) throws IOException {
        Map<String, Object> token = authService.oaCallback(code);
        resp.sendRedirect("/customer/?token=" + token.get("token"));
    }

    /** 返回 JSON（供 SPA 路由处理，不走 302） */
    @GetMapping("/wechat-oa/callback-json")
    public ApiResponse<Map<String, Object>> oaCallbackJson(@RequestParam String code,
                                                            @RequestParam(required = false) String state) {
        return ApiResponse.ok(authService.oaCallback(code));
    }

    @GetMapping("/wechat-work/authorize")
    public void workAuthorize(@RequestParam(name = "redirect_uri", required = false) String redirectUri,
                              @RequestParam(required = false) String state,
                              @RequestHeader(value = "Referer", required = false) String referer,
                              HttpServletRequest request,
                              HttpServletResponse resp) throws IOException {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/wechat-work/callback-json");
        String s = state == null ? UUID.randomUUID().toString() : state;
        log.info("[OAuth-Authorize] wechat-work redirectUri={} state={}", finalRedirectUri, s);
        resp.sendRedirect(authService.workAuthorizeUrl(finalRedirectUri, s));
    }

    @GetMapping("/wechat-work/authorize-json")
    public ApiResponse<Map<String, String>> workAuthorizeJson(
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(required = false) String state,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpServletRequest request) {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/wechat-work/callback-json");
        String s = state == null ? UUID.randomUUID().toString() : state;
        String url = authService.workAuthorizeUrl(finalRedirectUri, s);
        return ApiResponse.ok(Map.of("url", url, "state", s));
    }

    @GetMapping("/wechat-work/callback")
    public void workCallback(@RequestParam String code,
                             @RequestParam(required = false) String state,
                             HttpServletResponse resp) throws IOException {
        Map<String, Object> token = authService.workCallback(code);
        resp.sendRedirect("/agent/?token=" + token.get("token"));
    }

    @GetMapping("/wechat-work/callback-json")
    public ApiResponse<Map<String, Object>> workCallbackJson(@RequestParam String code,
                                                             @RequestParam(required = false) String state) {
        return ApiResponse.ok(authService.workCallback(code));
    }

    // ============ GitHub OAuth ============
    @GetMapping("/github/authorize")
    public void githubAuthorize(@RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 @RequestHeader(value = "Referer", required = false) String referer,
                                 HttpServletRequest request,
                                 HttpServletResponse resp) throws IOException {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/github/callback-json");
        log.info("[OAuth-Authorize] github redirectUri={} scope={}", finalRedirectUri, scope);
        resp.sendRedirect(authService.githubAuthorizeUrl(finalRedirectUri, scope));
    }

    @GetMapping("/github/authorize-json")
    public ApiResponse<Map<String, String>> githubAuthorizeJson(
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpServletRequest request) {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/github/callback-json");
        String url = authService.githubAuthorizeUrl(finalRedirectUri, scope);
        return ApiResponse.ok(Map.of("url", url));
    }

    @GetMapping("/github/callback")
    public void githubCallback(@RequestParam String code,
                                @RequestParam String state,
                                HttpServletResponse resp) throws IOException {
        try {
            Map<String, Object> token = authService.githubCallback(code, state);
            resp.sendRedirect("/customer/?token=" + token.get("token"));
        } catch (Exception e) {
            resp.sendRedirect("/login/?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/github/callback-json")
    public ApiResponse<Map<String, Object>> githubCallbackJson(@RequestParam String code, @RequestParam String state) {
        return ApiResponse.ok(authService.githubCallback(code, state));
    }

    // ============ Google OAuth ============
    @GetMapping("/google/authorize")
    public void googleAuthorize(@RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 @RequestHeader(value = "Referer", required = false) String referer,
                                 HttpServletRequest request,
                                 HttpServletResponse resp) throws IOException {
        String finalRedirectUri = resolveRedirectUri(redirectUri, referer, request, "/auth/google/callback-json");
        log.info("[OAuth-Authorize] google redirectUri={} scope={}", finalRedirectUri, scope);
        resp.sendRedirect(authService.googleAuthorizeUrl(finalRedirectUri, scope));
    }

    @GetMapping("/google/authorize-json")
    public ApiResponse<Map<String, String>> googleAuthorizeJson(
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String redirect_uri,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpServletRequest request) {
        String r = redirect_uri != null && !redirect_uri.isBlank() ? redirect_uri : redirectUri;
        String finalRedirectUri = resolveRedirectUri(r, referer, request, "/auth/google/callback-json");
        String url = authService.googleAuthorizeUrl(finalRedirectUri, scope);
        return ApiResponse.ok(Map.of("url", url));
    }

    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam String code,
                                @RequestParam String state,
                                @RequestParam String redirect_uri,
                                HttpServletResponse resp) throws IOException {
        try {
            Map<String, Object> token = authService.googleCallback(code, state, redirect_uri);
            resp.sendRedirect("/customer/?token=" + token.get("token"));
        } catch (Exception e) {
            resp.sendRedirect("/login/?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/google/callback-json")
    public ApiResponse<Map<String, Object>> googleCallbackJson(@RequestParam String code, @RequestParam String state, @RequestParam String redirect_uri) {
        return ApiResponse.ok(authService.googleCallback(code, state, redirect_uri));
    }

    // ============ 用户名 + 密码 ============
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterReq req) {
        if (req.getPassword() == null || req.getUsername() == null) {
            throw new ApiException(400, "用户名/密码必填");
        }
        // v2.2.35: 密码强度校验
        validatePasswordStrength(req.getPassword());
        return ApiResponse.ok(authService.register(req.getUsername(), req.getPassword(),
                req.getPhone(), req.getCode(), req.getNickname()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@RequestBody LoginReq req,
                                                              HttpServletRequest http,
                                                              HttpServletResponse resp) {
        Map<String, Object> body = authService.loginByPassword(req.getUsername(), req.getPassword(),
                clientIp(http));
        body.put("csrf", csrfIssuer.issue(resp));  // 登录成功下发 CSRF Token（Cookie + 响应体）
        return ApiResponse.ok(body);
    }

    // ============ 手机号 + 验证码 ============
    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSms(@RequestBody PhoneReq req) {
        return ApiResponse.ok(authService.sendSmsCode(req.getPhone()));
    }

    @PostMapping("/login-phone")
    public ApiResponse<Map<String, Object>> loginByPhone(@RequestBody PhoneLoginReq req,
                                                           HttpServletResponse resp) {
        Map<String, Object> body = authService.loginByPhone(req.getPhone(), req.getCode());
        body.put("csrf", csrfIssuer.issue(resp));
        return ApiResponse.ok(body);
    }

    // ============ 管理员 ============
    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody LoginReq req,
                                                         HttpServletResponse resp) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new ApiException(400, "账号/密码必填");
        }
        Map<String, Object> body = authService.adminLogin(req.getUsername(), req.getPassword());
        body.put("csrf", csrfIssuer.issue(resp));
        return ApiResponse.ok(body);
    }

    /**
     * v2.2.40: 坐席账号密码登录
     *
     * <pre>
     *   POST /auth/agent/login
     *   { username, password }
     *   ← { token, customerId, role: 'AGENT', channel: 'AGENT_PWD', ... }
     * </pre>
     */
    @PostMapping("/agent/login")
    public ApiResponse<Map<String, Object>> agentLogin(@RequestBody LoginReq req,
                                                         HttpServletRequest http,
                                                         HttpServletResponse resp) {
        Map<String, Object> body = authService.agentLoginByPassword(req.getUsername(), req.getPassword(),
                clientIp(http));
        body.put("csrf", csrfIssuer.issue(resp));
        return ApiResponse.ok(body);
    }

    /** v2.2.40: 重置坐席密码 (管理员操作) */
    @PostMapping("/admin/reset-agent-password")
    public ApiResponse<Map<String, Object>> resetAgentPassword(@RequestBody ResetAgentPwdReq req) {
        // 仅允许重置 customerId 以 a- 开头的坐席账号
        if (req.getCustomerId() == null || !req.getCustomerId().startsWith("a-")) {
            throw new ApiException(400, "customerId 必须是坐席账号（a- 前缀）");
        }
        WechatUser u = userRepo.findByCustomerId(req.getCustomerId())
                .orElseThrow(() -> new ApiException(404, "坐席不存在"));
        u.setPasswordHash(CryptoUtils.hashPassword(req.getNewPassword()));
        userRepo.save(u);
        // 让该坐席旧 token 全部失效
        blacklistService.bumpUserVersion(u.getCustomerId());
        log.info("[Agent] reset password for {}", req.getCustomerId());
        return ApiResponse.ok(Map.of("msg", "password reset", "customerId", u.getCustomerId()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String auth) {
        return ApiResponse.ok(Map.of("token", auth.replace("Bearer ", "")));
    }

    /**
     * 登出（v2.2.35）：将当前 token 加入黑名单，下次请求会被拦截
     *
     * <pre>
     *   POST /auth/logout
     *   Header: Authorization: Bearer eyJhbGc...
     *   ← { code: 0, msg: "ok" }
     * </pre>
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            long ttl = jwtUtils.parse(token) != null
                    ? Math.max(60, jwtUtils.parse(token).getExpiration().getTime() / 1000 - System.currentTimeMillis() / 1000)
                    : 86400;
            blacklistService.revoke(token, ttl);
            log.info("[Logout] token revoked, ttl={}s", ttl);
        }
        return ApiResponse.ok(Map.of("msg", "logged out"));
    }

    /**
     * 踢人/封号（v2.2.35）：管理员调用，让该用户所有 token 失效
     *
     * <pre>
     *   POST /auth/admin/revoke-user?customerId=c-xxx
     *   ← { code: 0, msg: "ok", version: 5 }
     * </pre>
     */
    @PostMapping("/admin/revoke-user")
    public ApiResponse<Map<String, Object>> revokeUser(@RequestParam String customerId) {
        Long v = blacklistService.bumpUserVersion(customerId);
        return ApiResponse.ok(Map.of("msg", "user revoked", "version", v));
    }

    /** 手机号实名认证查询 (v1.8.0: cs-im 反洗钱 / 适当性检查调用) */
    @GetMapping("/verify/phone")
    public ApiResponse<Map<String, Object>> verifyPhone(@RequestParam String customerId) {
        boolean verified = authService.verifyPhone(customerId);
        return ApiResponse.ok(Map.of("customerId", customerId, "verified", verified));
    }

    // ============ v2.0.0 微信小程序 / 公众号 H5 ============

    /**
     * 微信小程序登录（POST /auth/wx-mini/login）
     *
     * <p>小程序前端：
     * <pre>
     *   wx.login({
     *     success: res => request.post('/auth/wx-mini/login', {
     *       jsCode: res.code,
     *       encryptedData: '...',
     *       iv: '...'
     *     })
     *   })
     * </pre>
     */
    @PostMapping("/wx-mini/login")
    public ApiResponse<Map<String, Object>> wxMiniLogin(@RequestBody WxMiniLoginReq req) {
        if (req.getJsCode() == null || req.getJsCode().isBlank()) {
            throw new ApiException(400, "jsCode 必填");
        }
        return ApiResponse.ok(wxMiniService.login(req.getJsCode(), req.getEncryptedData(), req.getIv()));
    }

    /**
     * 微信公众号 H5 入口（GET /auth/wx-oa/h5-entry）
     *
     * <p>公众号菜单配置跳转到此 URL，客户点击后：
     * <ol>
     *   <li>OAuth 授权拿到 openid + 用户信息</li>
     *   <li>重定向到 /#/customer?token=xxx&customerId=xxx（内嵌客服）</li>
     * </ol>
     */
    @GetMapping("/wx-oa/h5-entry")
    public void wxOaH5Entry(@RequestParam(required = false) String code,
                              @RequestParam(required = false) String state,
                              HttpServletResponse resp) throws IOException {
        // 复用现有的 OA callback 逻辑
        if (code != null) {
            try {
                Map<String, Object> token = authService.oaCallback(code);
                String redirectUrl = "/#/customer?token=" + token.get("token")
                        + "&customerId=" + token.get("customerId")
                        + "&from=wx-oa";
                resp.sendRedirect(redirectUrl);
                return;
            } catch (Exception e) {
                resp.sendRedirect("/#/login?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
        }
        // 没有 code → 跳转到 OAuth 授权页
        String authorizeUrl = authService.oaAuthorizeUrl("/auth/wx-oa/h5-entry", UUID.randomUUID().toString());
        resp.sendRedirect(authorizeUrl);
    }

    // ============ v2.2.28 微信 JS-SDK / 消息推送 ============

    /**
     * JS-SDK 签名 (GET /auth/wechat-oa/js-sign)
     *
     * <p>公众号 H5 页面初始化 wx.config 所需的 4 个字段。
     * 前端调用：
     * <pre>
     *   const { data } = await request.get('/auth/wechat-oa/js-sign', {
     *     params: { url: location.href.split('#')[0] }
     *   })
     *   wx.config({ appId: data.appId, timestamp: data.timestamp,
     *     nonceStr: data.nonceStr, signature: data.signature, jsApiList: [...] })
     * </pre>
     */
    @GetMapping("/wechat-oa/js-sign")
    public ApiResponse<Map<String, String>> jsSign(@RequestParam String url) {
        return ApiResponse.ok(jsSignService.sign(url));
    }

    /**
     * 发送客服消息 (POST /auth/wechat-oa/customer-message)
     *
     * <p>48 小时窗口内主动联系客户。
     * <pre>
     *   POST { openid, content }
     *   ← { errcode: 0, errmsg: "ok", msg_id: "..." }
     * </pre>
     */
    @PostMapping("/wechat-oa/customer-message")
    public ApiResponse<Map<String, Object>> customerMessage(@RequestBody CustomerMsgReq req) {
        if (req.getOpenid() == null || req.getContent() == null) {
            throw new ApiException(400, "openid/content 必填");
        }
        return ApiResponse.ok(pushService.sendCustomerMessage(req.getOpenid(), req.getContent()));
    }

    /**
     * 发送模板消息 (POST /auth/wechat-oa/template-send)
     *
     * <pre>
     *   POST { openid, templateId, data: {first: {value:"..."}}, url, miniprogramAppid, miniprogramPagepath }
     * </pre>
     */
    @PostMapping("/wechat-oa/template-send")
    public ApiResponse<Map<String, Object>> templateSend(@RequestBody TemplateMsgReq req) {
        if (req.getOpenid() == null || req.getTemplateId() == null) {
            throw new ApiException(400, "openid/templateId 必填");
        }
        return ApiResponse.ok(pushService.sendTemplateMessage(
                req.getOpenid(), req.getTemplateId(), req.getData(),
                req.getUrl(), req.getMiniprogramAppid(), req.getMiniprogramPagepath()));
    }

    /**
     * 发送小程序订阅消息 (POST /auth/wx-mini/subscribe-send)
     *
     * <p>需用户在客户端先授权（wx.requestSubscribeMessage）。
     * <pre>
     *   POST { openid, templateId, data: {thing1: {value:"工单已处理"}}, page }
     * </pre>
     */
    @PostMapping("/wx-mini/subscribe-send")
    public ApiResponse<Map<String, Object>> miniSubscribeSend(@RequestBody MiniSubReq req) {
        if (req.getOpenid() == null || req.getTemplateId() == null) {
            throw new ApiException(400, "openid/templateId 必填");
        }
        return ApiResponse.ok(miniSubscribeService.send(
                req.getOpenid(), req.getTemplateId(), req.getData(), req.getPage()));
    }

    /** 小程序登录请求体 */
    @Data
    public static class WxMiniLoginReq {
        /** wx.login() 返回的临时凭证 */
        private String jsCode;
        /** 加密数据（手机号，可选） */
        private String encryptedData;
        /** 初始向量 */
        private String iv;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0] : req.getRemoteAddr();
    }

    /**
     * 密码强度校验（v2.2.35）
     *
     * <p>要求：8-32 位 + 同时包含字母和数字
     */
    private static void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new ApiException(400, "密码至少 8 位");
        }
        if (password.length() > 32) {
            throw new ApiException(400, "密码不能超过 32 位");
        }
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        if (!hasLetter || !hasDigit) {
            throw new ApiException(400, "密码必须同时包含字母和数字");
        }
    }

    /**
     * 智能推断 OAuth redirect_uri（开箱即用核心）
     *
     * <p>优先级：
     * <ol>
     *   <li>请求参数 redirectUri（前端明确传的）</li>
     *   <li>Referer header（从哪个页面点过来取 origin）</li>
     *   <li>请求 Host / X-Forwarded-Host 拼 origin</li>
     *   <li>默认值（后端 callback-json，前端轮询拿 token）</li>
     * </ol>
     */
    private String resolveRedirectUri(String param, String referer,
                                       HttpServletRequest request, String defaultPath) {
        // 1. 参数优先
        if (param != null && !param.isBlank()) {
            return param;
        }
        // 2. Referer
        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URI u = java.net.URI.create(referer);
                String origin = u.getScheme() + "://" + u.getHost()
                        + (u.getPort() > 0 ? ":" + u.getPort() : "");
                return origin + defaultPath;
            } catch (Exception ignored) { }
        }
        // 3. 从 request host 拼
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        if (host != null && !host.isBlank()) {
            return scheme + "://" + host + defaultPath;
        }
        // 4. 默认本机（开发环境）
        return "http://127.0.0.1:9001" + defaultPath;
    }

    /** v2.2.68: 访客登录请求体 (用 deviceId 作为客户唯一标识) */
    @Data public static class SilentReq { private String deviceId; }
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

    // ============ v2.2.28 微信推送请求体 ============

    @Data public static class CustomerMsgReq {
        private String openid;
        private String content;
    }

    @Data public static class TemplateMsgReq {
        private String openid;
        private String templateId;
        private Map<String, Object> data;
        private String url;
        private String miniprogramAppid;
        private String miniprogramPagepath;
    }

    @Data public static class MiniSubReq {
        private String openid;
        private String templateId;
        private Map<String, Object> data;
        private String page;
    }

    /** v2.2.40: 重置坐席密码 */
    @Data public static class ResetAgentPwdReq {
        private String customerId;
        private String newPassword;
    }
}