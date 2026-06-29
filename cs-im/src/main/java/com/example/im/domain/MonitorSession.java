package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MonitorSession 会话级录像头 (v2.2.97 国密加密录像)
 *
 * <p>每个客服会话一条; 用于快速查找会话是否有录像、总计多少片、留存到何时
 */
@Data
@TableName("monitor_session")
public class MonitorSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String customerId;
    private String agentUsername;

    private LocalDateTime startedAt;
    private LocalDateTime lastSegmentAt;
    private Integer segmentCount;
    private Long totalBytes;

    private String status;   // RECORDING / UPLOADING / ENDED / FAILED
    private LocalDateTime retentionUntil;
    private String sm3ChainRoot;    // v2.3.0: 会话开始时 hash 链根, 防会话级篡改

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
