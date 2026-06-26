package com.example.im.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 离线推送服务：
 * <ul>
 *   <li>用户离线时（WS 断开）消息暂存 Redis Stream</li>
 *   <li>定时任务扫描未推送消息</li>
 *   <li>推送通道：企业微信应用消息 API（内部员工）/ 公众号模板消息（外部客户）</li>
 * </ul>
 *
 * Mock 模式：仅记录日志，不真实调用微信 API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflinePushService {

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Value("${wechat.work.mock:true}")
    private boolean workMock;
    @Value("${wechat.oa.mock:true}")
    private boolean oaMock;
    @Value("${wechat.work.corp-id:demo-corp}")
    private String corpId;
    @Value("${wechat.work.agent-id:demo-agent}")
    private String agentId;
    @Value("${wechat.work.app-secret:demo-secret}")
    private String appSecret;
    @Value("${wechat.oa.app-id:demo-oa-appid}")
    private String oaAppId;
    @Value("${wechat.oa.app-secret:demo-oa-secret}")
    private String oaAppSecret;

    /** 是否在线（Redis 标记，由 WS connect/disconnect 维护） */
    public boolean isOnline(String userId) {
        return Boolean.TRUE.equals(redis.hasKey("online:" + userId));
    }

    public void markOnline(String userId) {
        redis.opsForValue().set("online:" + userId, "1", Duration.ofMinutes(2));
    }
    public void markOffline(String userId) {
        redis.delete("online:" + userId);
    }

    /** 离线消息入队（Redis Stream） */
    public void enqueue(String userId, String channel, ObjectNode payload) {
        Map<String, String> map = new HashMap<>();
        map.put("userId", userId);
        map.put("channel", channel);
        map.put("payload", payload.toString());
        map.put("ts", String.valueOf(System.currentTimeMillis()));
        redis.opsForStream().add("offline-msg", map);
        log.info("[OfflinePush] enqueued user={} channel={}", userId, channel);
    }

    /** 定时任务：扫描离线消息并推送 */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void flush() {
        var messages = redis.opsForStream().range("offline-msg",
                org.springframework.data.domain.Range.unbounded());
        if (messages == null || messages.isEmpty()) return;

        for (var msg : messages) {
            Map<String, String> body = (Map<String, String>) (Map) msg.getValue();
            String userId = body.get("userId");
            if (isOnline(userId)) {
                redis.opsForStream().delete("offline-msg", msg.getId());
                continue;
            }
            try {
                push(userId, body.get("channel"), body.get("payload"));
                redis.opsForStream().delete("offline-msg", msg.getId());
            } catch (Exception e) {
                log.warn("[OfflinePush] push failed for {}: {}", userId, e.getMessage());
            }
        }
    }

    private void push(String userId, String channel, String payload) {
        if ("WORK".equalsIgnoreCase(channel)) {
            sendWorkAppMsg(userId, payload);
        } else {
            sendOaTemplateMsg(userId, payload);
        }
    }

    /** 企业微信应用消息 */
    private void sendWorkAppMsg(String userid, String payload) {
        if (workMock) {
            log.info("[OfflinePush-MOCK-WORK] user={} payload={}", userid, payload);
            return;
        }
        // 真实实现：1) gettoken 2) message/send
    }

    /** 公众号模板消息 */
    private void sendOaTemplateMsg(String openid, String payload) {
        if (oaMock) {
            log.info("[OfflinePush-MOCK-OA] openid={} payload={}", openid, payload);
            return;
        }
        // 真实实现：1) gettoken 2) template/send
    }
}