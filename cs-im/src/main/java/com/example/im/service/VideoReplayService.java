package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.repo.ChatMessageMapper;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频回溯业务（v1.9.0）
 *
 * <p>真实业务场景：
 * <ol>
 *   <li>每次客户 / 坐席发送消息时，自动记录"视频帧"（消息内容 + 时间戳）</li>
 *   <li>会话结束后，可生成完整回放视频：按时间轴顺序播放所有消息 + 用户头像变化</li>
 *   <li>前端用 video.js / canvas 实现播放器</li>
 *   <li>视频帧可通过 ffmpeg 或 Playwright 截图脚本预渲染（离线）</li>
 * </ol>
 *
 * <p>当前实现：
 * <ul>
 *   <li>消息自动入库时按 createdAt 排序，时间戳就是帧位置</li>
 *   <li>前端播放器按消息时间戳 + 间隔（如 1.5 秒/条）播放</li>
 *   <li>提供 /im/message/replay 端点返回结构化数据</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoReplayService {

    /** 消息仓储 */
    private final ChatMessageRepo messageRepo;

    /** 消息 Mapper（直接调用） */
    private final ChatMessageMapper messageMapper;

    /** 会话 Mapper */
    private final ChatSessionMapper sessionMapper;

    /**
     * 生成会话视频回溯数据
     *
     * @param sessionId 会话 ID
     * @return { sessionId, customerId, agentUsername, startTime, endTime, frames: [...] }
     */
    public Map<String, Object> generateReplay(Long sessionId) {
        // 1) 查会话
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new ApiException(404, "会话不存在");

        // 2) 查所有消息
        List<ChatMessage> messages = messageMapper.findBySessionIdOrderByIdAsc(sessionId);

        // 3) 转视频帧（每条消息是一帧）
        List<Map<String, Object>> frames = new java.util.ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            Map<String, Object> frame = new HashMap<>();
            frame.put("index", i);                                // 帧序号
            frame.put("messageId", m.getId());                    // 消息 ID
            frame.put("fromUser", m.getFromUser());                // 发送方
            frame.put("fromRole", m.getFromRole());                // 角色
            frame.put("content", m.getContent());                  // 内容
            frame.put("type", m.getType());                        // 类型
            frame.put("timestamp", m.getCreatedAt());              // 时间戳
            frame.put("videoFrameUrl", m.getVideoFrameUrl());      // 帧截图 URL（可空）
            frames.add(frame);
        }

        // 4) 计算每帧间隔（默认 1.5 秒；首帧 0，最后一帧与会话结束时间对齐）
        if (frames.size() >= 2) {
            ChatMessage first = messages.get(0);
            ChatMessage last = messages.get(messages.size() - 1);
            long totalMs = java.time.Duration.between(first.getCreatedAt(), last.getCreatedAt()).toMillis();
            if (totalMs > 0) {
                long intervalMs = totalMs / (frames.size() - 1);
                for (int i = 0; i < frames.size(); i++) {
                    Map<String, Object> f = frames.get(i);
                    f.put("offsetMs", intervalMs * i);             // 该帧相对起始的偏移（毫秒）
                }
            }
        }

        // 5) 组装返回
        Map<String, Object> replay = new HashMap<>();
        replay.put("sessionId", sessionId);
        replay.put("customerId", session.getCustomerId());
        replay.put("agentUsername", session.getAgentUsername());
        replay.put("startTime", session.getCreatedAt());
        replay.put("endTime", session.getEndedAt() != null ? session.getEndedAt() : LocalDateTime.now());
        replay.put("frameCount", frames.size());
        replay.put("frames", frames);
        // 如果会话已绑定回放视频 URL，直接返回
        replay.put("videoUrl", session.getVideoReplayUrl());

        return replay;
    }

    /**
     * 设置会话的视频回放 URL（管理员上传视频后回调）
     */
    public void setReplayUrl(Long sessionId, String videoUrl) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw new ApiException(404, "会话不存在");
        s.setVideoReplayUrl(videoUrl);
        sessionMapper.updateById(s);
        log.info("[Replay] session {} 设置回放视频: {}", sessionId, videoUrl);
    }

    /**
     * 给消息打帧截图 URL（用于增强回放体验）
     */
    public void setFrameUrl(Long messageId, String frameUrl) {
        ChatMessage m = messageMapper.selectById(messageId);
        if (m == null) return;
        m.setVideoFrameUrl(frameUrl);
        messageMapper.updateById(m);
    }
}