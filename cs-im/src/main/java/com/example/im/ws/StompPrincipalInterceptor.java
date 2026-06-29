package com.example.im.ws;

import com.example.common.msg.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * v2.3.0 STOMP ChannelInterceptor
 *
 * <p>CONNECT 时:
 * <ol>
 *   <li>从 handshake attributes 拿 PRINCIPAL, set 到 session user</li>
 *   <li>WsPushService.register (本实例 user 在线)</li>
 * </ol>
 *
 * <p>DISCONNECT 时:
 * <ol>
 *   <li>WsPushService.unregister</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompPrincipalInterceptor implements ChannelInterceptor {

    private final WsPushService wsPushService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try {
            StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
            StompCommand cmd = acc.getCommand();
            if (cmd == null) return message;

            Map<String, Object> attrs = acc.getSessionAttributes();
            if (cmd == StompCommand.CONNECT) {
                if (attrs != null) {
                    Object principalObj = attrs.get("PRINCIPAL");
                    if (principalObj instanceof Principal p) {
                        acc.setUser(p);
                        wsPushService.register(p.getName());
                        log.info("[Stomp] CONNECT user={}", p.getName());
                    }
                }
            } else if (cmd == StompCommand.DISCONNECT) {
                Principal p = acc.getUser();
                if (p != null) {
                    wsPushService.unregister(p.getName());
                    log.info("[Stomp] DISCONNECT user={}", p.getName());
                }
            }
        } catch (Exception e) {
            log.warn("[Stomp] preSend err: {}", e.toString());
        }
        return message;
    }
}