package com.example.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 设备检测器 (v2.2.39)
 *
 * <p>根据 User-Agent 判断客户端类型，给不同端推荐不同 OAuth 方式：
 * <ul>
 *   <li>PC 浏览器 → 公众号 / 企微 扫码登录（PC 端 snsapi_base 不支持）</li>
 *   <li>手机浏览器（非微信）→ 企微扫码（公众号必须在微信内）</li>
 *   <li>微信内浏览器 → 公众号 snsapi_userinfo（用户点确认）</li>
 *   <li>微信小程序 → wx.login() + 静默授权</li>
 * </ul>
 */
@Component
public class DeviceDetector {

    public enum DeviceType {
        PC,        // PC 浏览器
        MOBILE,    // 手机浏览器
        WECHAT_OA, // 微信内浏览器（公众号场景）
        MINI       // 微信小程序
    }

    /**
     * 检测设备类型
     *
     * @param request HTTP 请求
     * @return 设备类型
     */
    public DeviceType detect(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return detect(ua);
    }

    /** 纯字符串检测（无 request 依赖） */
    public DeviceType detect(String userAgent) {
        if (userAgent == null) return DeviceType.PC;

        String ua = userAgent.toLowerCase();

        // 微信小程序特征
        if (ua.contains("miniprogram") || ua.contains("wxapp")) {
            return DeviceType.MINI;
        }

        // 微信内浏览器（公众号 H5）
        if (ua.contains("micromessenger")) {
            return DeviceType.WECHAT_OA;
        }

        // 移动设备
        if (ua.contains("android") || ua.contains("iphone") ||
            ua.contains("ipad") || ua.contains("ipod") ||
            ua.contains("windows phone") || ua.contains("mobile")) {
            return DeviceType.MOBILE;
        }

        return DeviceType.PC;
    }

    /**
     * 推荐 OAuth provider 列表
     *
     * <p>根据设备类型推荐：
     * <ul>
     *   <li>PC → 公众号 + 企微 + GitHub + Google</li>
     *   <li>MOBILE（非微信内）→ 企微 + Google（公众号不能用）</li>
     *   <li>WECHAT_OA → 公众号（最自然）</li>
     *   <li>MINI → 小程序（不能用网页 OAuth）</li>
     * </ul>
     */
    public String[] recommendProviders(DeviceType type) {
        switch (type) {
            case PC:
                return new String[]{"wechat-oa", "wechat-work", "github", "google"};
            case MOBILE:
                // 手机非微信浏览器：公众号不能在手机浏览器登录
                return new String[]{"wechat-work", "google"};
            case WECHAT_OA:
                // 微信内浏览器：公众号首选
                return new String[]{"wechat-oa", "wechat-work"};
            case MINI:
                // 小程序：返回空，由小程序端用 wx.login
                return new String[]{};
            default:
                return new String[]{"wechat-oa", "wechat-work"};
        }
    }

    /**
     * 推荐的 OAuth scope（公众号）
     */
    public String recommendScope(DeviceType type) {
        if (type == DeviceType.WECHAT_OA) {
            // 微信内浏览器：静默授权可用（userinfo 同样支持）
            // snsapi_base 在微信内浏览器仍可工作（拿到 openid）
            return "snsapi_base";
        }
        // PC / 手机浏览器：必须 snsapi_userinfo（snsapi_base 已废弃）
        return "snsapi_userinfo";
    }
}