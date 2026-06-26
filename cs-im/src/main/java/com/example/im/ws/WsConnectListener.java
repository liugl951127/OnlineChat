package com.example.im.ws;

import com.example.im.service.OfflinePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsConnectListener {

    private final OfflinePushService offlinePush;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor a = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = a.getSessionAttributes();
        if (attrs == null) return;
        String cid = (String) attrs.get("customerId");
        String agent = (String) attrs.get("agentUsername");
        if (cid != null) offlinePush.markOnline(cid);
        if (agent != null) offlinePush.markOnline(agent);
        log.info("[WS] connected cid={} agent={}", cid, agent);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor a = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = a.getSessionAttributes();
        if (attrs == null) return;
        String cid = (String) attrs.get("customerId");
        String agent = (String) attrs.get("agentUsername");
        if (cid != null) offlinePush.markOffline(cid);
        if (agent != null) offlinePush.markOffline(agent);
        log.info("[WS] disconnected cid={} agent={}", cid, agent);
    }
}