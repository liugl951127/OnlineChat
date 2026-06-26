package com.example.im.ws;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public final class StompSessionUtils {
    private StompSessionUtils() {}

    public static String customerIdOf(Message<?> msg) { return attr(msg, "customerId"); }
    public static String agentUsernameOf(Message<?> msg) { return attr(msg, "agentUsername"); }

    private static String attr(Message<?> msg, String key) {
        StompHeaderAccessor a = StompHeaderAccessor.wrap(msg);
        Object v = a.getSessionAttributes() == null ? null : a.getSessionAttributes().get(key);
        return v == null ? null : v.toString();
    }
}