package com.example.auth.service;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.example.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证核心服务
 *
 * <p>支持登录方式：
 * <ul>
 *   <li>静默登录（公众号 H5）</li>
 *   <li>OAuth（公众号 / 企微）</li>
 *   <li>用户名 + 密码</li>
 *   <li>手机号 + 验证码</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final WechatUserRepo userRepo;
    private final WechatOaClient oaClient;
    private final WechatWorkClient workClient;
    private final GithubOAuthClient githubClient;
    private final GoogleOAuthClient googleClient;
    private final OAuthStateCache stateCache;
    private final SmsService smsService;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redis;
    private final RateLimiter loginRateLimiter;

    @Value("${cs.phone.secret:please-change-this-32byte-aes-secret}")
    private String phoneSecret;

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 15;

    // ==================== 客户：静默 / OAuth ====================

    @Transactional
    public Map<String, Object> silentLogin(String tempCode) {
        if (tempCode == null || tempCode.isBlank()) {
            tempCode = "auto-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String openid = "oa-silent-" + tempCode;
        WechatUser u = userRepo.findByOpenid(openid).orElseGet(() -> {
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid).openid(openid).nickname("访客").build());
        });
        return tokenResponse(u, "OA");
    }

    @Transactional
    public Map<String, Object> oaCallback(String code) {
        Map<String, String> info = oaClient.exchangeCode(code);
        String openid = info.get("openid");
        WechatUser u = userRepo.findByOpenid(openid).orElseGet(() -> {
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid).openid(openid).unionid(info.get("unionid"))
                    .nickname(info.getOrDefault("nickname", "客户")).build());
        });
        return tokenResponse(u, "OA");
    }

    @Transactional
    public Map<String, Object> workCallback(String code) {
        Map<String, String> info = workClient.exchangeCode(code);
        String wwUserid = info.get("userid");
        WechatUser u = userRepo.findByWwUserid(wwUserid).orElseGet(() -> {
            String cid = "a-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid).wwUserid(wwUserid).nickname(info.getOrDefault("name", "坐席"))
                    .phoneMasked(SensitiveUtils.maskMobile(info.getOrDefault("mobile", ""))).build());
        });
        Map<String, Object> resp = tokenResponse(u, "WORK");
        resp.put("role", "AGENT");
        resp.put("skills", "ALL");
        return resp;
    }

    // ==================== 用户名 + 密码 ====================

    /** 注册（用户名 + 密码 + 手机号） */
    @Transactional
    public Map<String, Object> register(String username, String password, String phone, String code, String nickname) {
        validateUsername(username);
        // 校验手机号验证码
        if (phone != null && !phone.isBlank()) {
            smsService.verifyCode(phone, code);
        }
        if (userRepo.existsByUsername(username)) {
            throw new ApiException(409, "用户名已被占用");
        }
        String phoneEnc = phone == null ? null : CryptoUtils.encryptPhone(phone, phoneSecret);
        if (phoneEnc != null && userRepo.existsByPhoneEnc(phoneEnc)) {
            throw new ApiException(409, "手机号已注册");
        }

        WechatUser u = WechatUser.builder()
                .customerId("c-" + UUID.randomUUID().toString().substring(0, 12))
                .username(username)
                .passwordHash(CryptoUtils.hashPassword(password))
                .phoneEnc(phoneEnc)
                .phoneVerified(phoneEnc != null ? 1 : 0)
                .phoneMasked(phone == null ? null : SensitiveUtils.maskMobile(phone))
                .nickname(nickname == null || nickname.isBlank() ? username : nickname)
                .loginFailCount(0)
                .build();
        userRepo.save(u);
        log.info("[Auth] registered username={} customerId={}", username, u.getCustomerId());
        return tokenResponse(u, "OA");
    }

    /** 用户名 + 密码登录 */
    @Transactional
    public Map<String, Object> loginByPassword(String username, String password, String ip) {
        if (!loginRateLimiter.tryAcquire("login-pwd:" + username + ":" + (ip == null ? "" : ip))) {
            throw new ApiException(429, "尝试次数过多，请 1 分钟后再试");
        }
        if (username == null || password == null) {
            throw new ApiException(400, "用户名/密码必填");
        }
        WechatUser u = userRepo.findByUsername(username)
                .orElseThrow(() -> {
                    recordFail(null, username);
                    return new ApiException(401, "用户名或密码错误");
                });

        // 检查锁定
        if (u.getLockUntil() != null && u.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException(423, "账号已被锁定至 " + u.getLockUntil());
        }
        // 校验密码
        if (!CryptoUtils.verifyPassword(password, u.getPasswordHash())) {
            recordFail(u, username);
            throw new ApiException(401, "用户名或密码错误");
        }

        // 成功：清零失败计数
        u.setLoginFailCount(0);
        u.setLockUntil(null);
        userRepo.save(u);
        return tokenResponse(u, "OA");
    }

    private void recordFail(WechatUser u, String username) {
        if (u == null) return;
        u.setLoginFailCount((u.getLoginFailCount() == null ? 0 : u.getLoginFailCount()) + 1);
        if (u.getLoginFailCount() >= MAX_FAIL_COUNT) {
            u.setLockUntil(LocalDateTime.now().plus(Duration.ofMinutes(LOCK_MINUTES)));
            log.warn("[Auth] user {} locked until {}", username, u.getLockUntil());
        }
        userRepo.save(u);
    }

    // ==================== 手机号 + 验证码 ====================

    @Transactional
    public Map<String, Object> loginByPhone(String phone, String code) {
        validatePhone(phone);
        smsService.verifyCode(phone, code);
        String phoneEnc = CryptoUtils.encryptPhone(phone, phoneSecret);
        WechatUser u = userRepo.findByPhoneEnc(phoneEnc).orElseGet(() -> {
            // 新用户：自动注册
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid)
                    .phoneEnc(phoneEnc)
                    .phoneVerified(1)
                    .phoneMasked(SensitiveUtils.maskMobile(phone))
                    .nickname("用户" + phone.substring(phone.length() - 4))
                    .loginFailCount(0)
                    .build());
        });
        return tokenResponse(u, "OA");
    }

    /** 发送验证码 */
    public Map<String, Object> sendSmsCode(String phone) {
        return smsService.sendCode(phone);
    }

    // ==================== 管理员 ====================

    public Map<String, Object> adminLogin(String username, String password) {
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
        redis.opsForValue().set("token:" + u.getCustomerId(), token, Duration.ofHours(24));

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("customerId", u.getCustomerId());
        resp.put("username", u.getUsername());
        resp.put("nickname", u.getNickname());
        resp.put("avatar", u.getAvatar());
        resp.put("phoneMasked", u.getPhoneMasked());
        resp.put("channel", channel);
        return resp;
    }

    public String oaAuthorizeUrl(String redirectUri, String state) {
        return oaClient.authorizeUrl(redirectUri, state, "snsapi_base");
    }

    public String workAuthorizeUrl(String redirectUri, String state) {
        return workClient.authorizeUrl(redirectUri, state);
    }

    // ==================== OAuth: GitHub / Google ====================

    public String githubAuthorizeUrl(String redirectUri, String scope) {
        String state = stateCache.generate("github");
        return githubClient.authorizeUrl(redirectUri, state, scope);
    }

    @Transactional
    public Map<String, Object> githubCallback(String code, String state) {
        if (!stateCache.verifyAndConsume("github", state)) {
            throw new ApiException(400, "无效的 state（可能为伪造请求或已过期）");
        }
        String accessToken = githubClient.exchangeCode(code);
        Map<String, String> info = githubClient.fetchUser(accessToken);
        return oauthUpsert("GITHUB", info, "github", "https://api.dicebear.com/7.x/identicon/svg?seed=" + info.get("login"));
    }

    public String googleAuthorizeUrl(String redirectUri, String scope) {
        String state = stateCache.generate("google");
        return googleClient.authorizeUrl(redirectUri, state, scope);
    }

    @Transactional
    public Map<String, Object> googleCallback(String code, String state, String redirectUri) {
        if (!stateCache.verifyAndConsume("google", state)) {
            throw new ApiException(400, "无效的 state（可能为伪造请求或已过期）");
        }
        String accessToken = googleClient.exchangeCode(code, redirectUri);
        Map<String, String> info = googleClient.fetchUser(accessToken);
        return oauthUpsert("GOOGLE", info, "google", info.getOrDefault("picture", ""));
    }

    /** OAuth 账号 upsert：首次登录创建用户，之后只更新头像 */
    @Transactional
    public Map<String, Object> oauthUpsert(String provider, Map<String, String> info, String channel, String fallbackAvatar) {
        String providerUserId = info.get("id") != null ? info.get("id") : info.get("sub");
        if (providerUserId == null || providerUserId.isEmpty()) {
            throw new ApiException(500, "OAuth provider 未返回用户 ID");
        }
        WechatUser u = userRepo.findByProviderAndProviderUserId(provider, providerUserId).orElseGet(() -> {
            String cid = (provider.equals("GITHUB") ? "gh-" : "gg-") + java.util.UUID.randomUUID().toString().substring(0, 12);
            return userRepo.save(WechatUser.builder()
                    .customerId(cid)
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .nickname(info.getOrDefault("name", info.getOrDefault("login", provider + "-user")))
                    .avatar(info.getOrDefault("avatar_url", info.getOrDefault("picture", fallbackAvatar)))
                    .loginFailCount(0)
                    .build());
        });
        // 更新头像（可能被用户换过头像）
        String newAvatar = info.get("avatar_url");
        if (newAvatar == null) newAvatar = info.get("picture");
        if (newAvatar != null && !newAvatar.isEmpty()) {
            u.setAvatar(newAvatar);
            userRepo.save(u);
        }
        log.info("[OAuth] {} user {} login as {}", provider, providerUserId, u.getCustomerId());
        return tokenResponse(u, channel);
    }

    private void validateUsername(String u) {
        if (u == null || u.length() < 3 || u.length() > 32) {
            throw new ApiException(400, "用户名长度需 3-32 位");
        }
        if (!u.matches("[a-zA-Z0-9_\\-@.]+")) {
            throw new ApiException(400, "用户名仅支持字母数字下划线 - @ .");
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new ApiException(400, "手机号格式错误");
        }
    }

    /** 查询客户手机号实名认证状态 (v1.8.0: cs-im 反洗钱 / 适当性检查调用) */
    public boolean verifyPhone(String customerId) {
        if (customerId == null) return false;
        // mock fallback: real_ 前缀认为已认证
        if (customerId.startsWith("real_")) return true;
        return userRepo.findByCustomerId(customerId)
                .map(u -> Boolean.TRUE.equals(u.getPhoneVerified()))
                .orElse(false);
    }
}