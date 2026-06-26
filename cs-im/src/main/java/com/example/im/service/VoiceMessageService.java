package com.example.im.service;

import com.example.im.domain.VoiceMessage;
import com.example.im.repo.VoiceMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 语音消息服务 — 短语音 + ASR 转写
 *
 * <p>生产可对接：
 * <ul>
 *   <li>音频存储 — 阿里云 OSS / 腾讯云 COS</li>
 *   <li>ASR — 阿里云录音文件识别 / 腾讯云 ASR / Whisper</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceMessageService {

    private final VoiceMessageMapper voiceMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 上传语音消息（mock OSS 存储）
     */
    public VoiceMessage upload(Long sessionId, String fromId, String fromRole,
                                Integer durationSec, Integer fileSizeKb, String audioBase64) {
        VoiceMessage vm = new VoiceMessage();
        vm.setSessionId(sessionId);
        vm.setFromId(fromId);
        vm.setFromRole(fromRole);
        vm.setDurationSec(durationSec);
        vm.setFileSizeKb(fileSizeKb);
        vm.setAudioUrl("https://mock-oss.example.com/voice/" + UUID.randomUUID() + ".webm");
        vm.setWaveformData(mockWaveform(durationSec));
        vm.setTranscriptionStatus("PENDING");
        voiceMapper.insert(vm);

        // 推送给会话另一方
        notifyPeer(sessionId, fromId, vm);

        // 异步 ASR
        asrTranscribeAsync(vm);

        log.info("[Voice] 上传 voiceId={} sessionId={} duration={}s size={}KB",
            vm.getId(), sessionId, durationSec, fileSizeKb);
        return vm;
    }

    /**
     * 异步 ASR — mock 转写
     */
    @Async
    public void asrTranscribeAsync(VoiceMessage vm) {
        try {
            // Mock：直接返回一个示例转写
            String mock = "语音消息 (" + vm.getDurationSec() + "秒)";
            vm.setTranscription(mock);
            vm.setTranscriptionStatus("SUCCESS");
            voiceMapper.updateById(vm);
            log.info("[Voice] ASR 完成 voiceId={} text={}", vm.getId(), mock);
        } catch (Exception e) {
            vm.setTranscriptionStatus("FAILED");
            voiceMapper.updateById(vm);
            log.error("[Voice] ASR 失败 voiceId={}", vm.getId(), e);
        }
    }

    /**
     * 查询会话全部语音消息
     */
    public List<VoiceMessage> listBySession(Long sessionId) {
        return voiceMapper.findBySessionId(sessionId);
    }

    private void notifyPeer(Long sessionId, String fromId, VoiceMessage vm) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "VOICE_MESSAGE");
            payload.put("voiceId", vm.getId());
            payload.put("sessionId", sessionId);
            payload.put("fromId", fromId);
            payload.put("audioUrl", vm.getAudioUrl());
            payload.put("durationSec", vm.getDurationSec());
            payload.put("waveformData", vm.getWaveformData());
            // 简化推送给固定 prefix（生产按实际用户 ID）
            messagingTemplate.convertAndSendToUser(fromId, "/user/queue/voice", payload);
        } catch (Exception e) {
            log.warn("[Voice] WebSocket 推送失败（对端未连接）: {}", e.getMessage());
        }
    }

    /**
     * Mock 波形数据（10 个高度值 0-100）
     */
    private String mockWaveform(int durationSec) {
        StringBuilder sb = new StringBuilder("[");
        int n = 10;
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            int h = 30 + (int) (Math.random() * 60);
            sb.append(h);
        }
        sb.append("]");
        return sb.toString();
    }
}