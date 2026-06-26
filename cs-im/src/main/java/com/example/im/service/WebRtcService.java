package com.example.im.service;

import com.example.im.domain.ScreenShareSession;
import com.example.im.repo.ScreenShareSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebRTC 信令服务 — 屏幕共享 / 视频客服
 *
 * <p>信令流程：
 * <ol>
 *   <li>坐席发起 → 创建 ScreenShareSession(INVITED)</li>
 *   <li>推送给客户 → 客户 RTCPeerConnection.createOffer() → 发回 sdpOffer</li>
 *   <li>坐席 setRemoteDescription + createAnswer → 发回 sdpAnswer</li>
 *   <li>双方交换 ICE candidates</li>
 *   <li>P2P 连接建立 → 状态 ACTIVE</li>
 * </ol>
 *
 * <p>生产环境可对接 TURN/STUN 服务器（coturn）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebRtcService {

    private final ScreenShareSessionMapper shareMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发起屏幕共享 — 坐席端调用
     */
    public ScreenShareSession initiate(Long sessionId, String agent, String customer) {
        ScreenShareSession s = new ScreenShareSession();
        s.setSessionId(sessionId);
        s.setInitiator("agent");
        s.setPeer(customer);
        s.setStatus("INVITED");
        shareMapper.insert(s);

        // 推送邀请给客户
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SCREEN_SHARE_INVITE");
        payload.put("shareId", s.getId());
        payload.put("sessionId", sessionId);
        payload.put("agent", agent);
        payload.put("turnServers", turnServers());
        messagingTemplate.convertAndSendToUser(customer, "/user/queue/screen-share", payload);

        log.info("[WebRTC] 发起屏幕共享 shareId={} sessionId={} agent={} -> customer={}",
            s.getId(), sessionId, agent, customer);
        return s;
    }

    /**
     * 客户接受邀请
     */
    public void accept(Long shareId, String sdpAnswer) {
        ScreenShareSession s = shareMapper.selectById(shareId);
        if (s == null) throw new IllegalArgumentException("screen share not found: " + shareId);
        s.setStatus("ACTIVE");
        s.setSdpAnswer(sdpAnswer);
        s.setStartedAt(LocalDateTime.now());
        shareMapper.updateById(s);

        // 推送给坐席
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SCREEN_SHARE_ACCEPTED");
        payload.put("shareId", shareId);
        payload.put("sdpAnswer", sdpAnswer);
        messagingTemplate.convertAndSendToUser(s.getInitiator().equals("agent") ? findAgent(s.getSessionId()) : s.getInitiator(),
            "/user/queue/screen-share", payload);

        log.info("[WebRTC] 客户接受 shareId={}", shareId);
    }

    /**
     * 客户拒绝
     */
    public void reject(Long shareId) {
        ScreenShareSession s = shareMapper.selectById(shareId);
        if (s == null) return;
        s.setStatus("REJECTED");
        s.setEndedAt(LocalDateTime.now());
        shareMapper.updateById(s);

        // 推送给坐席
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SCREEN_SHARE_REJECTED");
        payload.put("shareId", shareId);
        messagingTemplate.convertAndSendToUser(s.getInitiator(), "/user/queue/screen-share", payload);

        log.info("[WebRTC] 客户拒绝 shareId={}", shareId);
    }

    /**
     * 结束屏幕共享
     */
    public void end(Long shareId) {
        ScreenShareSession s = shareMapper.selectById(shareId);
        if (s == null) return;
        s.setStatus("ENDED");
        s.setEndedAt(LocalDateTime.now());
        if (s.getStartedAt() != null) {
            s.setDurationSec((int) Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds());
        }
        shareMapper.updateById(s);

        // 通知双方
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SCREEN_SHARE_ENDED");
        payload.put("shareId", shareId);
        payload.put("durationSec", s.getDurationSec());
        messagingTemplate.convertAndSendToUser(s.getInitiator(), "/user/queue/screen-share", payload);
        messagingTemplate.convertAndSendToUser(s.getPeer(), "/user/queue/screen-share", payload);

        log.info("[WebRTC] 结束屏幕共享 shareId={} durationSec={}", shareId, s.getDurationSec());
    }

    /**
     * ICE candidate 中继
     */
    public void relayIceCandidate(Long shareId, String from, String candidateJson) {
        ScreenShareSession s = shareMapper.selectById(shareId);
        if (s == null) return;
        String to = from.equals(s.getInitiator()) ? s.getPeer() : s.getInitiator();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ICE_CANDIDATE");
        payload.put("shareId", shareId);
        payload.put("candidate", candidateJson);
        messagingTemplate.convertAndSendToUser(to, "/user/queue/screen-share", payload);
    }

    private String findAgent(Long sessionId) {
        return "agent-1";
    }

    /**
     * TURN/STUN 服务器列表（生产应放配置）
     */
    private Object turnServers() {
        return List.of(
            Map.of("urls", "stun:stun.l.google.com:19302"),
            Map.of("urls", "turn:turn.example.com:3478",
                "username", "user",
                "credential", "pass")
        );
    }
}