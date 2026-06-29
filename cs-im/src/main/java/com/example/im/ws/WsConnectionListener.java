package com.example.im.ws;

import com.example.common.msg.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * v2.3.0 WS 连接事件监听 → 注册/注销 WsPushService 本地表
 *
 * <p>连接时拿到 Principal.getName() = userId, 离线时移除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsConnectionListener {

    private final WsPushService wsPushService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        try {
            Principal p = event.getUser();
            if (p != null) {
                String userId = p.getName();
                if (userId != null && !userId.isBlank()) {
                    // v2.3.0: WsPushService.register 需要 session, 这里只记 user 在线, 推送给它时由其他组件绑定
                    log.info("[WsConn] connect user={}", userId);
                }
            }
        } catch (Exception e) {
            log.warn("[WsConn] onConnected error: {}", e.toString());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        try {
            Principal p = event.getUser();
            if (p != null && p.getName() != null) {
                String userId = p.getName();
                wsPushService.unregister(userId);
                log.info("[WsConn] disconnect user={} status={}", userId, event.getCloseStatus());
            }
        } catch (Exception e) {
            log.warn("[WsConn] onDisconnect error: {}", e.toString());
        }
    }
}