package com.example.common.config;

import com.example.common.msg.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * v2.3.0 Redis Pub/Sub listener
 *
 * <p>订阅 {@code ws:push:*} 所有频道, 把消息分发到 {@link WsPushService}
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.listener.RedisMessageListenerContainer")
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            WsPushService wsPushService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // 监听 ws:push:* 所有频道
        container.addMessageListener((message, pattern) -> {
            try {
                String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                // 格式: msgId|payload
                int sep = body.indexOf('|');
                if (sep < 0) return;
                String msgId = body.substring(0, sep);
                String payload = body.substring(sep + 1);
                // 频道格式 ws:push:{userId}
                String userId = channel.substring("ws:push:".length());
                wsPushService.onBroadcastReceived(userId, msgId, payload);
            } catch (Exception e) {
                log.warn("[RedisPubSub] listener 处理失败: {}", e.toString());
            }
        }, new PatternTopic("ws:push:*"));

        log.info("[RedisPubSub] 订阅 ws:push:* 已注册");
        return container;
    }
}