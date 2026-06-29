package com.example.im.ws;

import com.example.common.msg.WsPushService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * v2.3.0 WS 握手拦截器
 *
 * <p>职责:
 * <ol>
 *   <li>从 query 参数拿 userId/customerId/agentUsername</li>
 *   <li>在 attributes 写入 Principal (这样后续 @MessageMapping 能拿到)</li>
 *   <li>WSPushService 本地 register (会话开始时)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final WsPushService wsPushService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            HttpServletRequest req = servletReq.getServletRequest();
            String customerId = req.getParameter("customerId");
            String agentUsername = req.getParameter("agentUsername");
            String admin = req.getParameter("admin");

            putIfPresent(attributes, "customerId", customerId);
            putIfPresent(attributes, "agentUsername", agentUsername);
            putIfPresent(attributes, "admin", admin);

            // v2.3.0: 写入 Principal 让 STOMP CONNECT 时拿到, WsPushService 用它做本地 register
            final String principalName = customerId != null ? customerId
                    : (agentUsername != null ? agentUsername
                    : (admin != null ? admin : null));
            if (principalName != null) {
                attributes.put("PRINCIPAL_NAME", principalName);
                final String name = principalName;
                attributes.put("PRINCIPAL", new Principal() {
                    @Override public String getName() { return name; }
                });
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private static void putIfPresent(Map<String, Object> attrs, String key, String val) {
        if (val != null && !val.isBlank()) attrs.put(key, val);
    }
}