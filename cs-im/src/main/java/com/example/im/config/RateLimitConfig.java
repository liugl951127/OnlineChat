package com.example.im.config;

import com.example.common.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    /** 发消息限流：每用户每分钟 60 条 */
    @Bean(name = "messageRateLimiter")
    public RateLimiter messageRateLimiter() {
        return new RateLimiter(60, 60);
    }

    /** 撤回限流：每用户每分钟 10 次 */
    @Bean(name = "recallRateLimiter")
    public RateLimiter recallRateLimiter() {
        return new RateLimiter(10, 60);
    }

    /** 反应限流：每用户每分钟 60 次 */
    @Bean(name = "reactionRateLimiter")
    public RateLimiter reactionRateLimiter() {
        return new RateLimiter(60, 60);
    }
}