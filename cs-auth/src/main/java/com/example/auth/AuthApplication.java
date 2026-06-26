package com.example.auth;

import com.example.common.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"com.example.auth", "com.example.common"})
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    public JwtUtils jwtUtils(@Value("${cs.jwt.secret:default-jwt-secret-change-me-in-production-32-chars-min}") String secret,
                             @Value("${cs.jwt.ttl-ms:86400000}") long ttlMs) {
        return new JwtUtils(secret, ttlMs);
    }
}