package com.example.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具：网关与服务间认证。
 * 注意：生产环境密钥应从配置中心读取，不能硬编码。
 */
@Slf4j
public class JwtUtils {

    private final SecretKey key;
    private final long ttlMs;

    public JwtUtils(String secret, long ttlMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMs = ttlMs;
    }

    public String issue(String subject, Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            log.debug("[JWT] parse failed: {}", e.getMessage());
            return null;
        }
    }
}