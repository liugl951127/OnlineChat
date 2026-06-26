package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音消息 — 短语音 + ASR 转写
 *
 * <p>流程：
 * <ol>
 *   <li>前端 MediaRecorder 录 .webm/.wav</li>
 *   <li>上传 OSS / 后端 mock storage</li>
 *   <li>调 ASR 服务转文本（mock 返回固定 mock 转写）</li>
 *   <li>同时存储音频和转写文本（可搜索 + 可读）</li>
 * </ol>
 *
 * @author MiniMax
 */
@Data
@TableName("voice_message")
public class VoiceMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String fromId;
    private String fromRole;

    private String audioUrl;

    /** 时长（秒） */
    private Integer durationSec;

    private Integer fileSizeKb;

    /** 波形数据 JSON（用于播放条 UI） */
    private String waveformData;

    /** ASR 转写文本 */
    private String transcription;

    /** PENDING / SUCCESS / FAILED */
    private String transcriptionStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}