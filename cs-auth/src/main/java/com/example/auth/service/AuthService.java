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
    public Map<String, Object> silentLogin(String deviceId) {
        // v2.2.68: 用 deviceId 切丁客户唯一标识 (同一设备 -> 同一 customerId)
        // v2.2.73: final 变量用于 lambda (避免 effectively final 错误)
        // v2.2.74: 设备号 -> 查不到时也要合并公众号已关联用户
        final String finalDeviceId;
        if (deviceId == null || deviceId.isBlank()) {
            finalDeviceId = "auto-" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            finalDeviceId = deviceId;
        }
        String openid = "dev-" + finalDeviceId;
        // v2.2.75: existingCid 在 lambda 外生成, 避免 lambda 引用被重新赋值的变量
        String existingCid = "c-" + finalDeviceId.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(12, finalDeviceId.replaceAll("[^a-zA-Z0-9]", "").length()));
        if (existingCid.length() < 3) {
            existingCid = "c-" + UUID.randomUUID().toString().substring(0, 12);
        }
        final String finalExistingCid = existingCid;
        WechatUser u = userRepo.findByOpenid(openid).orElseGet(() -> {
            // v2.2.74: 检查是否已有该设备号的记录 (customerId 去重)
            // 场景: 客户先用设备号登录, 后来扫码公众号但 openid 变了
            //       只要 unionid/openid 关联同一客户, 设备号也复用
            // v2.2.75: 引用 finalExistingCid (避免 effectively final 问题)
            return userRepo.findByCustomerId(finalExistingCid).orElseGet(() ->
                userRepo.save(WechatUser.builder()
                    .customerId(finalExistingCid).openid(openid).nickname("访客").build())
            );
        });
        return tokenResponse(u, "OA");
    }

    @Transactional
    public Map<String, Object> oaCallback(String code) {
        // v2.2.69: 第二步 exchangeCode 拿 openid + access_token
        Map<String, String> info = oaClient.exchangeCode(code);
        String openid = info.get("openid");
        String accessToken = info.get("access_token");
        String unionid = info.get("unionid");

        // v2.2.69: 第三步 fetchUserInfo 拉详细资料 (已关注才返回, 未关注需重新授权)
        Map<String, String> userInfo = null;
        if (accessToken != null && !accessToken.isBlank()) {
            userInfo = oaClient.fetchUserInfo(accessToken, openid);
        }
        if (userInfo != null) {
            unionid = userInfo.getOrDefault("unionid", unionid);
        }

        String finalUnionid = unionid;
        String finalNickname = userInfo != null ? userInfo.get("nickname") : null;
        String finalAvatar = userInfo != null ? userInfo.get("avatar") : null;

        // v2.2.80: 检查是否已关注公众号
        Boolean subscribedNullable = oaClient.checkSubscribe(openid);
        boolean subscribed = subscribedNullable != null && subscribedNullable;
        if (subscribedNullable == null) {
            log.warn("[Auth] checkSubscribe 失败, 默认放过 openid={}", openid);
            subscribed = true;
        }

        if (!subscribed) {
            log.info("[Auth] 用户未关注公众号 openid={}, 需扫码关注", openid);
            Map<String, Object> errResp = new HashMap<>();
            errResp.put("openid", openid);
            errResp.put("unionid", finalUnionid);
            errResp.put("qrcodeUrl", oaClient.generateQrcodeUrl(openid));
            errResp.put("subscribeUrl", "https://mp.weixin.qq.com/s/" + openid);
            throw new ApiException(451, "请先关注公众号").withData(errResp);
        }

        // v2.2.74: 多层去重查找
        WechatUser u = userRepo.findByOpenid(openid).orElse(null);
        if (u == null && finalUnionid != null && !finalUnionid.isBlank()) {
            u = userRepo.findByUnionid(finalUnionid).orElse(null);
        }

        if (u == null) {
            // 都不存在 -> 新建, 使用 deviceId 或 UUID 生成 customerId
            String cid = "c-" + UUID.randomUUID().toString().substring(0, 12);
            final WechatUser newUser = WechatUser.builder()
                    .customerId(cid).openid(openid).unionid(finalUnionid)
                    .nickname(finalNickname != null ? finalNickname : "微信客户")
                    .avatar(finalAvatar)
                    .subscribeStatus(1)
                    .subscribeCheckedAt(LocalDateTime.now())
                    .build();
            u = userRepo.save(newUser);
        } else {
            // 已存在 -> 补全 openid/unionid (设备号用户扫码公众号后绑定的场景)
            if (u.getOpenid() == null || !u.getOpenid().equals(openid)) {
                u.setOpenid(openid);
            }
            if (finalUnionid != null && !finalUnionid.isBlank()
                && (u.getUnionid() == null || !u.getUnionid().equals(finalUnionid))) {
                u.setUnionid(finalUnionid);
            }
        }

        // v2.2.69: 已存在用户, 如果头像/昵称为空也补全
        if (u.getAvatar() == null && finalAvatar != null) {
            u.setAvatar(finalAvatar);
        }
        if ((u.getNickname() == null || u.getNickname().isBlank()) && finalNickname != null) {
            u.setNickname(finalNickname);
        }
        // v2.2.80: 更新关注状态
        u.setSubscribeStatus(1);
        u.setSubscribeCheckedAt(LocalDateTime.now());
        u = userRepo.save(u);

        Map<String, Object> resp = tokenResponse(u, "OA");
        resp.put("subscribed", subscribed);
        return resp;
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
        // v2.2.40: 根据 wechat_user.role 决定 AGENT / ADMIN（默认 AGENT）
        String role = u.getRole() != null ? u.getRole() : "AGENT";
        resp.put("role", role);
        resp.put("skills", "ALL");
        return resp;
    }

    /** v2.2.40: 坐席账号密码登录（与客户分开渠道） */
    @Transactional
    public Map<String, Object> agentLoginByPassword(String username, String password, String ip) {
        if (!loginRateLimiter.tryAcquire("agent-pwd:" + username + ":" + (ip == null ? "" : ip))) {
            throw new ApiException(429, "尝试次数过多，请 1 分钟后再试");
        }
        if (username == null || password == null) {
            throw new ApiException(400, "账号/密码必填");
        }
        WechatUser u = userRepo.findByUsername(username)
                .orElseThrow(() -> {
                    recordFail(null, username);
                    return new ApiException(401, "坐席账号或密码错误");
                });

        if (u.getLockUntil() != null && u.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException(423, "账号已被锁定至 " + u.getLockUntil());
        }
        if (!CryptoUtils.verifyPassword(password, u.getPasswordHash())) {
            recordFail(u, username);
            throw new ApiException(401, "坐席账号或密码错误");
        }
        // 必须 customerId 开头是 a- 或者是 ADMIN/AGENT 角色
        if (u.getCustomerId() != null && !u.getCustomerId().startsWith("a-")
                && !"AGENT".equals(u.getRole()) && !"ADMIN".equals(u.getRole())) {
            throw new ApiException(403, "该账号不是坐席账号");
        }

        // 成功：清零失败计数
        u.setLoginFailCount(0);
        u.setLockUntil(null);
        userRepo.save(u);
        Map<String, Object> resp = tokenResponse(u, "AGENT_PWD");
        resp.put("role", u.getRole() != null ? u.getRole() : "AGENT");
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
        // v2.2.80: 密码登录 channel = LOCAL (不调微信, 完全独立)
        return tokenResponse(u, "LOCAL");
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
        // v2.2.83: 兼容两种密码 (admin/admin123, 与 seed.sql 一致)
        if (!"admin".equals(username) || (!"admin".equals(password) && !"admin123".equals(password))) {
            throw new ApiException(401, "账号或密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", "admin");
        claims.put("displayName", "系统管理员");
        claims.put("channel", "LOCAL");
        claims.put("adminLevel", "SUPER");
        String token = jwtUtils.issue("admin", claims);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", "ADMIN");
        result.put("userId", "admin");
        result.put("displayName", "系统管理员");
        result.put("channel", "LOCAL");
        return result;
    }

    // ==================== 通用 ====================

    public Map<String, Object> buildTokenResponse(WechatUser u, String channel) {
        return tokenResponse(u, channel);
    }

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
        // 完整用户信息（OAuth 回调需要）
        resp.put("openid", u.getOpenid());
        resp.put("unionid", u.getUnionid());
        resp.put("wwUserid", u.getWwUserid());
        resp.put("provider", u.getProvider() != null ? u.getProvider() : channel);
        resp.put("providerUserId", u.getProviderUserId());
        resp.put("role", u.getRole() != null ? u.getRole() : "CUSTOMER");
        resp.put("status", u.getStatus());
        resp.put("lastLoginTime", u.getLastLoginTime());
        resp.put("createdAt", u.getCreatedAt());
        return resp;
    }

    public String oaAuthorizeUrl(String redirectUri, String state) {
        return oaAuthorizeUrl(redirectUri, state, "snsapi_userinfo");
    }

    /**
     * v2.2.38: 支持自定义 scope
     *
     * <p>公众号默认 snsapi_userinfo（snsapi_base 在 PC 端已废弃 2022）
     * 静默授权仅在微信开放平台第三方 / 小程序支持
     */
    public String oaAuthorizeUrl(String redirectUri, String state, String scope) {
        return oaClient.authorizeUrl(redirectUri, state, scope);
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