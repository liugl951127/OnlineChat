package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContextHolder;
import com.example.common.msg.OfflineMessageStore;
import com.example.common.msg.WsPushService;
import com.example.im.domain.ChatLink;
import com.example.im.service.LinkService;
import com.example.im.service.MessageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * v2.3.0 链接推送 REST
 *
 * <p>坐席 → POST /api/im/agent/link 推送
 * <br>客户 → GET /api/im/link/{token} 打开 (返回 302 → targetUrl)
 *
 * <p>实现:
 * <ol>
 *   <li>坐席调用 create() 拿到 shortToken + 完整 chat_link 记录</li>
 *   <li>后端用 WsPushService 推 LINK_CARD 给客户 (chat message 推送同通道)</li>
 *   <li>客户 chat 页面收到 LINK_CARD, 显示卡片, 点击 → window.open(shortUrl)</li>
 *   <li>短链打开时调 /api/im/link/{token} → 服务端 resolve + 302</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;
    private final MessageService messageService;
    private final WsPushService wsPushService;
    private final @org.springframework.beans.factory.annotation.Qualifier("commonOfflineMessageStore") OfflineMessageStore offlineStore;

    /**
     * 坐席推送链接
     *
     * @param req.sessionId  会话 ID
     * @param req.targetUrl  要推送的 URL (已校验白名单)
     */
    @PostMapping("/agent/link")
    public ApiResponse<Map<String, Object>> pushLink(@RequestBody LinkPushRequest req) {
        var ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        if (!"AGENT".equals(ctx.getRole()) && !"ADMIN".equals(ctx.getRole())) {
            throw new ApiException(403, "仅坐席/管理员可推链接");
        }
        if (req.getSessionId() == null || req.getTargetUrl() == null) {
            throw new ApiException(400, "sessionId / targetUrl 必填");
        }

        // 1) 创建短链
        ChatLink link = linkService.create(req.getSessionId(), ctx.getUserId(), req.getTargetUrl());

        // 2) v2.3.0: 异步推 LINK 消息给客户 (Kafka + Pub/Sub)
        //   不阻塞 HTTP 返回, pushLink 必须 < 100ms
        //   失败兜底: 写 offline store 让客户进站时 drain
        String content = "[LINK]" + link.getShortToken() + "|" + link.getTargetUrl();
        final Long sessionId = req.getSessionId();
        final String agentId = ctx.getUserId();
        final String targetUrl = link.getTargetUrl();
        final String shortToken = link.getShortToken();
        final Long linkId = link.getId();
        new Thread(() -> {
            try {
                messageService.send(sessionId, agentId, agentId, "AGENT", content, "LINK");
            } catch (Exception e) {
                log.warn("[Link] async push fail, fallback offline: {}", e.getMessage());
                String payload = String.format(
                    "{\"type\":\"LINK_CARD\",\"sessionId\":%d,\"payload\":{\"token\":\"%s\",\"url\":\"%s\"}}",
                    sessionId, shortToken, targetUrl);
                offlineStore.push(agentId, "link-" + linkId, payload);
            }
        }, "link-push-" + linkId).start();

        Map<String, Object> ret = new HashMap<>();
        ret.put("linkId", link.getId());
        ret.put("shortToken", link.getShortToken());
        ret.put("shortUrl", "/api/im/link/" + link.getShortToken());
        ret.put("targetUrl", link.getTargetUrl());
        ret.put("expireAt", link.getExpireAt().toString());
        ret.put("oneTime", link.getMaxClicks() != null && link.getMaxClicks() == 1);
        return ApiResponse.ok(ret);
    }

    /**
     * 客户点击短链 → 服务端 resolve + 重定向
     */
    @GetMapping("/link/{token}")
    public org.springframework.http.ResponseEntity<Map<String, Object>> openLink(@PathVariable String token) {
        ChatLink link;
        try {
            link = linkService.resolve(token);
        } catch (ApiException e) {
            return org.springframework.http.ResponseEntity.status(e.getCode())
                .body(Map.of("error", e.getMessage()));
        }

        // 返回 JSON 让前端决定 window.open (避免 302 在某些环境下被拦截)
        Map<String, Object> ret = new HashMap<>();
        ret.put("ok", true);
        ret.put("url", link.getTargetUrl());
        ret.put("remaining", link.getMaxClicks() == null ? -1 :
            Math.max(0, link.getMaxClicks() - link.getClickCount()));
        return org.springframework.http.ResponseEntity.ok(ret);
    }

    @Data
    public static class LinkPushRequest {
        private Long sessionId;
        private String targetUrl;
    }
}