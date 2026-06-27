package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.im.domain.ScreenShareSession;
import com.example.im.domain.VoiceMessage;
import com.example.im.service.WebRtcService;
import com.example.im.service.VoiceMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多媒体 REST API — WebRTC 屏幕共享 + 语音消息
 */
@RestController
@RequestMapping("/im/media")
@RequiredArgsConstructor
public class MediaController {

    private final WebRtcService webRtcService;
    private final VoiceMessageService voiceService;

    // ---- 屏幕共享 ----

    @PostMapping("/screen-share/initiate")
    public ApiResponse<ScreenShareSession> initiateShare(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        String agent = (String) body.get("agent");
        String customer = (String) body.get("customer");
        return ApiResponse.ok(webRtcService.initiate(sessionId, agent, customer));
    }

    @PostMapping("/screen-share/{shareId}/accept")
    public ApiResponse<String> acceptShare(@PathVariable Long shareId, @RequestBody Map<String, Object> body) {
        String sdpAnswer = (String) body.get("sdpAnswer");
        webRtcService.accept(shareId, sdpAnswer);
        return ApiResponse.ok("accepted");
    }

    @PostMapping("/screen-share/{shareId}/reject")
    public ApiResponse<String> rejectShare(@PathVariable Long shareId) {
        webRtcService.reject(shareId);
        return ApiResponse.ok("rejected");
    }

    @PostMapping("/screen-share/{shareId}/end")
    public ApiResponse<String> endShare(@PathVariable Long shareId) {
        webRtcService.end(shareId);
        return ApiResponse.ok("ended");
    }

    @PostMapping("/screen-share/{shareId}/ice")
    public ApiResponse<String> relayIce(@PathVariable Long shareId, @RequestBody Map<String, Object> body) {
        String from = (String) body.get("from");
        String candidate = (String) body.get("candidate");
        webRtcService.relayIceCandidate(shareId, from, candidate);
        return ApiResponse.ok("relayed");
    }

    // ---- 语音消息 ----

    @PostMapping("/voice/upload")
    public ApiResponse<VoiceMessage> uploadVoice(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        String fromId = (String) body.get("fromId");
        String fromRole = (String) body.getOrDefault("fromRole", "customer");
        Integer durationSec = ((Number) body.get("durationSec")).intValue();
        Integer fileSizeKb = body.get("fileSizeKb") == null ? null
            : ((Number) body.get("fileSizeKb")).intValue();
        String audioBase64 = (String) body.get("audioBase64");

        // v2.2.35: 语音大小限制（防止内存炸弹，base64 4/3 倍）
        if (audioBase64 != null && audioBase64.length() > 5 * 1024 * 1024) {  // ~3.75MB binary
            throw new ApiException(400, "语音文件不能超过 3.75MB");
        }
        if (durationSec != null && durationSec > 300) {
            throw new ApiException(400, "语音时长不能超过 5 分钟");
        }

        VoiceMessage vm = voiceService.upload(sessionId, fromId, fromRole, durationSec, fileSizeKb, audioBase64);
        return ApiResponse.ok(vm);
    }

    @GetMapping("/voice/list")
    public ApiResponse<List<VoiceMessage>> listVoice(@RequestParam Long sessionId) {
        return ApiResponse.ok(voiceService.listBySession(sessionId));
    }
}