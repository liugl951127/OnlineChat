package com.example.auth.service;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.example.common.ApiException;
import com.example.common.JwtUtils;
import com.example.common.SensitiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WechatUserRepo userRepo;
    private final WechatOaClient oaClient;
    private final WechatWorkClient workClient;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redis;

    // ==================== 客户 ====================

    /** 静默登录：用临时 code（前端第一次访问拿到的伪随机串）换取 customerId + JWT */
    @Transactional
    public Map<String, Object> silentLogin(String tempCode) {
        if (tempCode == null || tempCode.isBlank()) {
            tempCode = "auto-" + UUID.randomUUID().toString().substring(0, 8);
        }
        // 伪逻辑：临时 code 复用为 openid 后缀（生产应换为更稳健的 ID）
        String openid = "oa-silent-" + tempCode;
        WechatUser u = userRepo.findByOpenid(openid).orElseGet(() -> {
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            WechatUser nu = WechatUser.builder()
                    .customerId(cid)
                    .openid(openid)
                    .nickname("访客")
                    .build();
            return userRepo.save(nu);
        });
        return tokenResponse(u, "OA");
    }

    /** 公众号 OAuth 回调 */
    @Transactional
    public Map<String, Object> oaCallback(String code) {
        Map<String, String> info = oaClient.exchangeCode(code);
        String openid = info.get("openid");
        WechatUser u = userRepo.findByOpenid(openid).orElseGet(() -> {
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid)
                    .openid(openid)
                    .unionid(info.get("unionid"))
                    .nickname(info.getOrDefault("nickname", "客户"))
                    .build());
        });
        return tokenResponse(u, "OA");
    }

    // ==================== 坐席 ====================

    /** 企微 OAuth 回调 */
    @Transactional
    public Map<String, Object> workCallback(String code) {
        Map<String, String> info = workClient.exchangeCode(code);
        String wwUserid = info.get("userid");
        WechatUser u = userRepo.findByWwUserid(wwUserid).orElseGet(() -> {
            String cid = "a-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid)
                    .wwUserid(wwUserid)
                    .nickname(info.getOrDefault("name", "坐席"))
                    .phoneMasked(SensitiveUtils.maskMobile(info.getOrDefault("mobile", "")))
                    .build());
        });
        Map<String, Object> resp = tokenResponse(u, "WORK");
        resp.put("role", "AGENT");
        resp.put("skills", "ALL");
        return resp;
    }

    // ==================== 管理员 ====================

    public Map<String, Object> adminLogin(String username, String password) {
        // 简化：admin/admin
        if (!"admin".equals(username) || !"admin".equals(password)) {
            throw new ApiException(401, "账号或密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", "admin");
        claims.put("displayName", "系统管理员");
        claims.put("channel", "LOCAL");
        claims.put("adminLevel", "SUPER");
        String token = jwtUtils.issue("admin", claims);
        return Map.of("token", token, "role", "ADMIN", "displayName", "系统管理员");
    }

    // ==================== 通用 ====================

    private Map<String, Object> tokenResponse(WechatUser u, String channel) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CUSTOMER");
        claims.put("userId", u.getCustomerId());
        claims.put("displayName", u.getNickname());
        claims.put("channel", channel);
        String token = jwtUtils.issue(u.getCustomerId(), claims);
        // 把 token 缓存到 Redis 用于单点登出
        redis.opsForValue().set("token:" + u.getCustomerId(), token, Duration.ofHours(24));
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("customerId", u.getCustomerId());
        resp.put("nickname", u.getNickname());
        resp.put("avatar", u.getAvatar());
        resp.put("channel", channel);
        return resp;
    }

    public String oaAuthorizeUrl(String redirectUri, String state) {
        return oaClient.authorizeUrl(redirectUri, state, "snsapi_base");
    }

    public String workAuthorizeUrl(String redirectUri, String state) {
        return workClient.authorizeUrl(redirectUri, state);
    }
}