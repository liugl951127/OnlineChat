package com.example.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * JWT Secret 安全检查 (v2.2.75)
 *
 * <p>启动后检查 JWT_SECRET 是否使用默认值, 如果是则警告.
 *
 * <p>判断依据: secret 是否包含 "CHANGE_ME" 或 "please-change-this" 关键字.
 *
 * <p>警告级别:
 * <ul>
 *   <li>默认值: WARN + 大字体提示</li>
 *   <li>自定义值: INFO + 长度提示</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtSecretWarning {

    @Value("${cs.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("==========================================================");
            log.error("  JWT_SECRET 未配置!");
            log.error("  启动失败风险: 任何 token 都被拒");
            log.error("  修复: 设置环境变量 JWT_SECRET=32+ 字节随机字符串");
            log.error("==========================================================");
            return;
        }

        boolean isDefault = jwtSecret.contains("CHANGE_ME")
                          || jwtSecret.contains("please-change-this")
                          || jwtSecret.startsWith("CHANGE_ME_")
                          || jwtSecret.length() < 32;

        if (isDefault) {
            log.warn("==========================================================");
            log.warn("  ⚠️ JWT_SECRET 使用默认值/弱密钥!");
            log.warn("  Profile: {}", profile);
            log.warn("  当前 secret 长度: {} 字节", jwtSecret.length());
            log.warn("  安全风险: 任何人都能伪造 JWT token");
            log.warn("  修复: 设置环境变量 JWT_SECRET=32+ 字节随机字符串");
            log.warn("  示例: export JWT_SECRET=\\$(openssl rand -base64 48)");
            log.warn("==========================================================");
        } else {
            log.info("✓ JWT_SECRET 已配置, 长度 {} 字节", jwtSecret.length());
            if (profile.equals("prod") || profile.equals("production")) {
                log.info("  当前 profile: {}, 安全状态: 良好", profile);
            }
        }
    }
}