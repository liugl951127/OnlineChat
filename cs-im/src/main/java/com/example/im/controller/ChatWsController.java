package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.WsEnvelope;
import com.example.common.msg.WsPushService;
import com.example.im.domain.ChatSession;
import com.example.im.service.MessageService;
import com.example.im.service.SessionService;
import com.example.im.ws.StompSessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final SessionService sessionService;
    private final MessageService messageService;
    private final WsPushService wsPushService;

    @MessageMapping("/customer/chat")
    public void fromCustomer(Message<?> rawMessage, WsEnvelope inbound) {
        String cid = StompSessionUtils.customerIdOf(rawMessage);
        if (cid == null) throw new ApiException(401, "未识别客户");
        ChatSession s = sessionService.findActive(cid);
        if (s == null) throw new ApiException(404, "无活跃会话");
        if (s.getStatus() != com.example.im.domain.SessionStatus.IN_SESSION) {
            throw new ApiException(400, "当前不在通话中，请通过 REST 调用机器人");
        }
        messageService.send(s.getId(), cid, cid, "CUSTOMER", inbound.getContent(), "TEXT");
    }

    @MessageMapping("/agent/chat")
    public void fromAgent(Message<?> rawMessage, WsEnvelope inbound) {
        String agent = StompSessionUtils.agentUsernameOf(rawMessage);
        if (agent == null) throw new ApiException(401, "未识别坐席");
        ChatSession s = sessionService.findById(inbound.getSessionId());
        if (s == null) throw new ApiException(404, "会话不存在");
        if (!agent.equals(s.getAgentUsername())) {
            throw new ApiException(403, "无权对该会话发言");
        }
        messageService.send(s.getId(), agent, agent, "AGENT", inbound.getContent(), "TEXT");
    }
}