package com.example.auth.service;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信小程序登录服务（v2.0.0）
 *
 * <p>真实业务流程：
 * <ol>
 *   <li>小程序前端调 wx.login() 拿到 jsCode</li>
 *   <li>POST /auth/wx-mini/login 传 jsCode + encryptedData + iv</li>
 *   <li>后端调微信接口 https://api.weixin.qq.com/sns/jscode2session
 *       拿 openid + session_key</li>
 *   <li>可选：用 session_key 解密 encryptedData 拿手机号</li>
 *   <li>用 openid 查 / 建用户，返回 JWT</li>
 * </ol>
 *
 * <p>Mock 模式：直接用 jsCode 作为 openId，方便测试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxMiniService {

    private final WechatUserRepo userRepo;
    private final AuthService authService;

    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Value("${wechat.mini.app-id:demo-mini-app-id}")
    private String appId;

    @Value("${wechat.mini.app-secret:demo-mini-app-secret}")
    private String appSecret;

    @Value("${wechat.mini.mock:true}")
    private boolean mock;

    /**
     * 小程序登录
     *
     * @param jsCode         微信返回的临时登录凭证
     * @param encryptedData  加密数据（手机号等，可选）
     * @param iv             初始向量（解密用，可选）
     * @return { token, customerId, openid, sessionKey, phone }
     */
    public Map<String, Object> login(String jsCode, String encryptedData, String iv) {
        // 1) jscode2session 拿 openid + session_key
        Map<String, String> wxResult;
        if (mock) {
            // Mock：直接用 jsCode 作为 openId
            wxResult = new HashMap<>();
            wxResult.put("openid", "mini-" + jsCode);
            wxResult.put("session_key", "mock-session-key-" + UUID.randomUUID());
        } else {
            wxResult = callWxJscode2session(jsCode);
        }
        String openid = wxResult.get("openid");
        String sessionKey = wxResult.get("session_key");

        // 2) 解密手机号（如果提供了 encryptedData）
        String phone = null;
        if (encryptedData != null && iv != null) {
            phone = decryptPhone(encryptedData, sessionKey, iv);
        }

        // 3) 查 / 建用户
        WechatUser user = userRepo.findByOpenid(openid).orElseGet(() -> {
            WechatUser u = new WechatUser();
            u.setCustomerId("c-mini-" + UUID.randomUUID().toString().substring(0, 12));
            u.setOpenid(openid);
            u.setUnionid(wxResult.get("unionid"));
            u.setNickname("小程序用户");
            u.setProvider("WECHAT_MINI");
            u.setProviderUserId(openid);
            u.setChannel("WECHAT_MINI");
            u.setRole("CUSTOMER");
            u.setStatus(1);
            return userRepo.save(u);
        });

        // 4) 如果解密到手机号，更新
        if (phone != null) {
            user.setPhoneVerified(1);
            userRepo.save(user);
        }

        // 5) 颁发 JWT
        Map<String, Object> token = authService.buildTokenResponse(user, "WECHAT_MINI");
        token.put("openid", openid);
        if (phone != null) token.put("phone", phone);

        log.info("[WxMini] 登录成功 customerId={} openid={}", user.getCustomerId(), openid);
        return token;
    }

    /**
     * 调微信 jscode2session 接口（真实模式）
     */
    private Map<String, String> callWxJscode2session(String jsCode) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appId, appSecret, jsCode
        );
        try {
            String body = http.getForObject(url, String.class);
            @SuppressWarnings("unchecked")
            Map<String, String> resp = json.readValue(body, Map.class);
            if (resp.containsKey("errcode") && !"0".equals(resp.get("errcode"))) {
                throw new RuntimeException("微信登录失败: " + resp.get("errmsg"));
            }
            return resp;
        } catch (Exception e) {
            log.warn("[WxMini] 调微信失败，降级 Mock: {}", e.getMessage());
            Map<String, String> mock = new HashMap<>();
            mock.put("openid", "mini-" + jsCode);
            mock.put("session_key", "fallback-" + UUID.randomUUID());
            return mock;
        }
    }

    /**
     * 解密手机号（Mock：直接返回 +86 开头的 11 位）
     *
     * <p>真实解密算法：AES-128-CBC，key = session_key[0..16]，iv = iv
     * 用 BouncyCastle / Apache Commons Codec
     */
    private String decryptPhone(String encryptedData, String sessionKey, String iv) {
        if (mock) return "13800138000";
        // 真实解密：
        // byte[] key = sessionKey.getBytes(StandardCharsets.UTF_8);
        // byte[] ivBytes = Base64.decode(iv);
        // byte[] encrypted = Base64.decode(encryptedData);
        // SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        // Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        // cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
        // byte[] decrypted = cipher.doFinal(encrypted);
        // String json = new String(decrypted, StandardCharsets.UTF_8);
        // JSONObject obj = JSON.parseObject(json);
        // return obj.getString("phoneNumber");
        return "13800138000";
    }
}