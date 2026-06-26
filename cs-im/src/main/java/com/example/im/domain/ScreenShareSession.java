package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 屏幕共享会话 — WebRTC 信令
 *
 * <p>工作流：
 * <ol>
 *   <li>坐席发起屏幕共享 → REST 创建会话</li>
 *   <li>前端 getDisplayMedia() → 拿到 MediaStream → 创建 RTCPeerConnection</li>
 *   <li>offer/answer/ICE candidate 通过 WebRTC 信令转发</li>
 *   <li>双方建立 P2P 连接 → 屏幕流传输</li>
 *   <li>坐席结束 → 关闭连接 → 更新状态 ENDED</li>
 * </ol>
 *
 * @author MiniMax
 */
@Data
@TableName("screen_share_session")
public class ScreenShareSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** agent / customer */
    private String initiator;

    private String peer;

    /** INVITED / ACTIVE / ENDED / REJECTED */
    private String status;

    private String sdpOffer;
    private String sdpAnswer;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /** 总时长（秒） */
    private Integer durationSec;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}