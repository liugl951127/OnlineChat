package com.example.im.config;

import com.example.common.msg.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import jakarta.annotation.PostConstruct;

/**
 * v2.3.0 把 cs-im 的 SimpMessagingTemplate 注册到 cs-common WsPushService
 *
 * <p>Pub/Sub 收到 ws:push:{userId} → cs-im 直接 convertAndSendToUser 推 STOMP 客户端
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WsPushDeliverConfig {

    private final WsPushService wsPushService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void register() {
        wsPushService.setLocalDeliver((userId, payload) -> {
            // STOMP user destination: /user/{userId}/queue/messages
            messagingTemplate.convertAndSendToUser(userId, "/queue/messages", payload);
        });
        log.info("[WsPushDeliver] cs-im 已注册 SimpMessagingTemplate 推送回调");
    }
}