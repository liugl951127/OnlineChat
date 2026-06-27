package com.example.auth.controller;

import com.example.auth.service.DeviceDetector;
import com.example.auth.service.DeviceDetector.DeviceType;
import com.example.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备自适应 OAuth 端点 (v2.2.39)
 *
 * <p>前端调用这个接口问"我应该展示哪些登录按钮 + 哪些能用 snsapi_base 静默"。
 *
 * <pre>
 *   GET /auth/oauth/recommend
 *   Header: User-Agent: Mozilla/5.0 (iPhone...)
 *   ← {
 *       "device": "MOBILE",
 *       "providers": ["wechat-work", "google"],
 *       "scope": "snsapi_userinfo",
 *       "note": "手机浏览器公众号不可用，建议用企微扫码"
 *     }
 * </pre>
 */
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class DeviceAuthController {

    private final DeviceDetector detector;

    @GetMapping("/recommend")
    public ApiResponse<Map<String, Object>> recommend(
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request) {
        DeviceType type = detector.detect(userAgent);
        String[] providers = detector.recommendProviders(type);
        String scope = detector.recommendScope(type);

        Map<String, Object> r = new HashMap<>();
        r.put("device", type.name());
        r.put("providers", providers);
        r.put("scope", scope);
        r.put("note", buildNote(type));
        return ApiResponse.ok(r);
    }

    private String buildNote(DeviceType type) {
        switch (type) {
            case PC:
                return "PC 浏览器：公众号扫码（snsapi_userinfo）+ 企微扫码 + GitHub + Google";
            case MOBILE:
                return "手机浏览器：公众号无法登录（需微信内浏览器），推荐企微扫码或 Google";
            case WECHAT_OA:
                return "微信内浏览器：公众号 snsapi_base 静默授权可用，无需弹窗";
            case MINI:
                return "小程序：请使用 wx.login() 调用 /auth/wx-mini/login";
            default:
                return "";
        }
    }
}