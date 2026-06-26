package com.example.auth.config;

import com.example.common.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthRateLimitConfig {

    /** 密码登录限流：每 IP/账号 每分钟 10 次（防爆破） */
    @Bean(name = "loginRateLimiter")
    public RateLimiter loginRateLimiter() {
        return new RateLimiter(10, 60);
    }

    /** 短信发送：每手机号 每 60 秒 1 次 + 每 5 分钟 3 次（按 limiter 串行校验） */
    @Bean(name = "smsSendLimiter")
    public RateLimiter smsSendLimiter() {
        return new RateLimiter(3, 300);
    }

    /** 短信验证：每手机号 每分钟 10 次 */
    @Bean(name = "smsVerifyLimiter")
    public RateLimiter smsVerifyLimiter() {
        return new RateLimiter(10, 60);
    }
}