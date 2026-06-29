package com.example.im.config;

import com.example.im.ws.StompPrincipalInterceptor;
import com.example.im.ws.WsHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket / STOMP 配置 (v2.3.0 性能优化)
 *
 * <p>变更:
 * <ul>
 *   <li>多 endpoint 拆分: /ws/customer /ws/agent /ws/admin /ws/system (按角色路由)</li>
 *   <li>setHeartbeatValue(20_000, 20_000): 客户端 20s 心跳 (默认 0 0 太死)</li>
 *   <li>setTaskScheduler: 心跳调度器, 提高连接存活率</li>
 *   <li>保留原 /ws 端点 (向后兼容)</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsHandshakeInterceptor handshakeInterceptor;
    private final StompPrincipalInterceptor stompPrincipalInterceptor;
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler webSocketHeartbeatScheduler =
            new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler() {{
                setPoolSize(1);
                setThreadNamePrefix("ws-heartbeat-");
                initialize();
            }};

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 通用 endpoint (兼容)
        registry.addEndpoint("/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // v2.3.0: 按角色拆分 (内部路由直接 dispatch, 减少 topic 匹配)
        registry.addEndpoint("/ws/customer")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws/agent")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws/admin")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // raw websocket (高级客户端)
        registry.addEndpoint("/ws/customer", "/ws/agent", "/ws/admin")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{20_000, 20_000})  // v2.3.0: 20s 心跳
                .setTaskScheduler(webSocketHeartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // v2.3.0: inbound 通道池 16 (默认 1)
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(32)
                .queueCapacity(1000)
                .keepAliveSeconds(60);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // v2.3.0: outbound 通道池大点 (推送并发高)
        registration.taskExecutor()
                .corePoolSize(8)
                .maxPoolSize(64)
                .queueCapacity(2000)
                .keepAliveSeconds(60);
    }
}