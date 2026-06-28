package com.example.auth.controller;

import com.example.auth.service.WechatOaClient;
import com.example.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号配置诊断 (v2.2.38)
 *
 * <p>返回公众号 OAuth 关键配置项，前端展示 + 后端诊断用。
 * 部署者根据返回信息去微信公众平台后台配置"网页授权域名"。
 *
 * <pre>
 *   GET /auth/wechat-oa/config
 *   ← {
 *       "appId": "wx1234567890abcdef",
 *       "appIdSet": true,
 *       "mock": false,
 *       "scopeDefault": "snsapi_userinfo",
 *       "scopeAvailable": ["snsapi_userinfo"],
 *       "note": "公众号必须配置网页授权域名才能回调，详见 README"
 *     }
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/auth/wechat-oa")
@RequiredArgsConstructor
public class WechatConfigController {

    private final WechatOaClient oaClient;

    @Value("${wechat.oa.app-id:demo-app-id}")
    private String appId;
    @Value("${wechat.oa.app-secret:demo-app-secret}")
    private String appSecret;
    @Value("${wechat.oa.mock:true}")
    private boolean mock;

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> r = new HashMap<>();
        r.put("appId", appId);
        r.put("appIdSet", !appId.startsWith("demo-") && !appId.startsWith("wx-your-"));
        r.put("appSecretSet", !appSecret.startsWith("demo-") && !appSecret.contains("your-"));
        r.put("mock", mock);
        // v2.2.38: 公众号 PC 端 snsapi_base 已废弃，只能用 snsapi_userinfo
        r.put("scopeDefault", "snsapi_userinfo");
        r.put("scopeAvailable", new String[]{"snsapi_userinfo"});
        r.put("scopeNote",
                "公众号从 2022 年起不支持 snsapi_base 静默授权，" +
                "只能用 snsapi_userinfo（用户点确认）。" +
                "静默授权仅微信开放平台第三方应用 + 微信小程序支持。");
        r.put("deployNote",
                "生产部署必须:\n" +
                "1) 微信公众号 → 设置 → 公众号设置 → 功能设置 → 网页授权域名 → 填你的公网域名\n" +
                "2) 域名必须 HTTPS\n" +
                "3) 域名不能带 http:// 或路径\n" +
                "4) 业务请求 redirect_uri 的域名必须与配置一致");
        return ApiResponse.ok(r);
    }

    /**
     * v2.2.80: 检查 openid 是否已关注公众号
     *
     * <p>前端拿到后:
     * <ul>
     *   <li>subscribed=true  -> 直接登录</li>
     *   <li>subscribed=false -> 弹二维码 + subscribeUrl 引导关注</li>
     *   <li>subscribed=null  -> API 失败, 让用户重试</li>
     * </ul>
     */
    @GetMapping("/subscribe-check")
    public ApiResponse<Map<String, Object>> subscribeCheck(@RequestParam String openid) {
        Boolean sub = oaClient.checkSubscribe(openid);
        Map<String, Object> r = new HashMap<>();
        r.put("openid", openid);
        r.put("subscribed", sub);
        r.put("subscribeUrl", "https://mp.weixin.qq.com/s/" + openid);
        return ApiResponse.ok(r);
    }

    /**
     * v2.2.80: 生成公众号关注二维码 URL (带场景值)
     *
     * <p>用于前端弹窗引导用户扫码关注.
     *
     * <p>sceneId 推荐用 openid (1-64 字符), 关注事件可识别是哪个用户拉起.
     */
    @PostMapping("/qrcode-for-subscribe")
    public ApiResponse<Map<String, Object>> qrcodeForSubscribe(@RequestBody Map<String, String> body) {
        String sceneId = body.getOrDefault("sceneId", "");
        if (sceneId.isBlank()) {
            return ApiResponse.fail(400, "sceneId 必填");
        }
        String url = oaClient.generateQrcodeUrl(sceneId);
        Map<String, Object> r = new HashMap<>();
        r.put("sceneId", sceneId);
        r.put("qrcodeUrl", url);
        r.put("subscribeUrl", "https://mp.weixin.qq.com/s/" + sceneId);
        return ApiResponse.ok(r);
    }
}