package com.example.auth;

import com.example.auth.service.TokenBlacklistService;
import com.example.common.JwtUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.example.auth", "com.example.common"})
@MapperScan("com.example.auth.repo")
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    public JwtUtils jwtUtils(@Value("${cs.jwt.secret:default-jwt-secret-change-me-in-production-32-chars-min}") String secret,
                             @Value("${cs.jwt.ttl-ms:86400000}") long ttlMs,
                             TokenBlacklistService blacklistService) {
        JwtUtils utils = new JwtUtils(secret, ttlMs);
        // v2.2.35: 注入黑名单检查器，踢人/登出后旧 token 自动失效
        utils.setTokenBlacklistChecker(blacklistService::isRevoked);
        return utils;
    }
}