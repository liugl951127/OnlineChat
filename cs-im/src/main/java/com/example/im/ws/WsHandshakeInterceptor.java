package com.example.im.ws;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器：从 query 取出 token（网关已校验）+ customerId/agentUsername
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            HttpServletRequest req = servletReq.getServletRequest();
            putIfPresent(attributes, "customerId", req.getParameter("customerId"));
            putIfPresent(attributes, "agentUsername", req.getParameter("agentUsername"));
            putIfPresent(attributes, "admin", req.getParameter("admin"));
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