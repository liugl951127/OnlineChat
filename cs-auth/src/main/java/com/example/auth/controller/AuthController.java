package com.example.auth.controller;

import com.example.auth.service.AuthService;
import com.example.auth.service.WechatJsSignService;
import com.example.auth.service.WechatPushService;
import com.example.auth.service.WxMiniService;
import com.example.auth.service.WxMiniSubscribeService;
import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.security.CsrfTokenIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
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
    private final CsrfTokenIssuer csrfIssuer;
    private final WxMiniService wxMiniService;
    private final WechatJsSignService jsSignService;
    private final WechatPushService pushService;
    private final WxMiniSubscribeService miniSubscribeService;

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

    /** 返回 JSON（供 SPA 路由处理，不走 302） */
    @GetMapping("/wechat-oa/callback-json")
    public ApiResponse<Map<String, Object>> oaCallbackJson(@RequestParam String code,
                                                            @RequestParam(required = false) String state) {
        return ApiResponse.ok(authService.oaCallback(code));
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

    @GetMapping("/wechat-work/callback-json")
    public ApiResponse<Map<String, Object>> workCallbackJson(@RequestParam String code,
                                                             @RequestParam(required = false) String state) {
        return ApiResponse.ok(authService.workCallback(code));
    }

    // ============ GitHub OAuth ============
    @GetMapping("/github/authorize")
    public void githubAuthorize(@RequestParam String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 HttpServletResponse resp) throws IOException {
        resp.sendRedirect(authService.githubAuthorizeUrl(redirectUri, scope));
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
    public void googleAuthorize(@RequestParam String redirectUri,
                                 @RequestParam(required = false) String scope,
                                 HttpServletResponse resp) throws IOException {
        resp.sendRedirect(authService.googleAuthorizeUrl(redirectUri, scope));
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

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestHeader("Authorization") String auth) {
        return ApiResponse.ok(Map.of("token", auth.replace("Bearer ", "")));
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
}