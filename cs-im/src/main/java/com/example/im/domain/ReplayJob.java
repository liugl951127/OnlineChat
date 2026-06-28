package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 视频合成任务（v2.2.78）
 *
 * <p>会话结束时触发，把 replay_frame 表里所有帧合并成 MP4 视频。
 *
 * <p>状态机：
 * <pre>
 *   PENDING ──► RUNNING ──► SUCCESS ──► (video_url)
 *                   │
 *                   └──► FAILED ──► (error_message)
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("replay_job")
public class ReplayJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private Integer frameCount;

    /** 合成后视频 URL (本地路径或 CDN) */
    private String videoUrl;

    /** 视频时长 (毫秒) */
    private Long durationMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}