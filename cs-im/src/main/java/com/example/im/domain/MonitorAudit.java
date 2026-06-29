package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 录像访问审计 (v2.3.0 合规)
 *
 * <p>谁能看, 谁下过, 谁导出过 — 全审计, 6 个月留存
 */
@Data
@TableName("monitor_audit")
public class MonitorAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String customerId;

    private String operatorId;
    private String operatorRole;
    private String operatorIp;

    /** VIEW / DOWNLOAD / EXPORT / DELETE / PLAYBACK_JUMP */
    private String action;

    private Integer segmentFrom;
    private Integer segmentTo;
    private String extra;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}