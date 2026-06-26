package com.example.common;

import io.jsonwebtoken.Claims;        // JWT 声明对象，存放 subject/exp/业务 claims
import io.jsonwebtoken.Jwts;          // JJWT 8 主入口
import io.jsonwebtoken.security.Keys; // JJWT 提供的对称密钥工厂
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;        // JDK 标准 SecretKey 类型
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类：负责 cs-auth 网关颁发的 Token 签发 + cs-gateway/cs-im 等下游服务的 Token 校验。
 * 算法：HMAC-SHA256，使用对称密钥，密钥长度 ≥ 32 字节（JJWT 要求）。
 * 注意：生产环境密钥应从 Nacos 配置中心或环境变量读取，禁止硬编码。
 */
@Slf4j
public class JwtUtils {

    /** 对称密钥（基于 HMAC-SHA256） */
    private final SecretKey key;

    /** Token 有效期（毫秒） */
    private final long ttlMs;

    /**
     * 构造方法
     *
     * @param secret 密钥字符串（UTF-8 编码，长度 ≥ 32 字节）
     * @param ttlMs  Token 有效期（毫秒）
     */
    public JwtUtils(String secret, long ttlMs) {
        // 从字符串生成 HMAC-SHA256 专用密钥（JJWT 自动取 SHA-256 摘要长度）
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // 缓存过期时间
        this.ttlMs = ttlMs;
    }

    /**
     * 签发 Token
     *
     * @param subject 用户标识（通常是 customerId / username / agentUsername）
     * @param claims  业务字段（role / displayName / channel / skills / adminLevel）
     * @return 紧凑型 JWT 字符串
     */
    public String issue(String subject, Map<String, Object> claims) {
        // 获取当前时间（iat 和 exp 的基准）
        Date now = new Date();
        // 构造 Token：subject + 自定义 claims + iat + exp + 签名
        return Jwts.builder()
                .subject(subject)                                // 设置 subject（用户唯一标识）
                .claims(claims)                                  // 注入业务字段（role/skills 等）
                .issuedAt(now)                                   // iat = now
                .expiration(new Date(now.getTime() + ttlMs))     // exp = now + ttl
                .signWith(key)                                   // 用 HMAC-SHA256 签名
                .compact();                                      // 生成紧凑型字符串
    }

    /**
     * 解析 Token，校验签名 + 校验过期
     *
     * @param token 待解析的 JWT 字符串
     * @return Claims 对象（解析失败返回 null）
     */
    public Claims parse(String token) {
        try {
            // JJWT 8.x：先用密钥构造 parser，再 parseSignedClaims 验证签名 + 校验 exp
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            // 解析失败（签名错 / 过期 / 格式错）→ 返回 null，调用方应抛 401
            log.debug("[JWT] parse failed: {}", e.getMessage());
            return null;
        }
    }
}