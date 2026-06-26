package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;       // ID 策略
import com.baomidou.mybatisplus.annotation.TableField;  // 字段填充
import com.baomidou.mybatisplus.annotation.TableId;     // 主键
import com.baomidou.mybatisplus.annotation.TableLogic;  // 逻辑删除
import com.baomidou.mybatisplus.annotation.TableName;  // 表名
import lombok.Data;                                      // Lombok

import java.time.LocalDateTime;                          // 时间类型

/**
 * 工单实体（cs_im.ticket 表）
 *
 * <p>v1.9.0 客服工单核心模型。
 *
 * <p>状态机：
 * <pre>
 *   OPEN ──► ASSIGNED ──► PROCESSING ──► RESOLVED ──► CLOSED
 *    │                     │                              ▲
 *    └──► CANCELLED        └──────────────────────────────┘
 * </pre>
 */
@Data
@TableName("ticket")
public class Ticket {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号（业务唯一，格式：TK + 时间戳 + 随机数） */
    private String ticketNo;

    /** 客户 ID */
    private String customerId;

    /** 处理坐席（可空，未分配时为 null） */
    private String agentUsername;

    /** 关联会话 ID（可空，从会话一键建单时填） */
    private Long sessionId;

    /** 工单标题 */
    private String title;

    /** 工单描述 */
    private String description;

    /** 分类：GENERAL / COMPLAINT / CONSULT / BUG */
    private String category;

    /** 优先级：LOW / NORMAL / HIGH / URGENT */
    private String priority;

    /** 状态：OPEN / ASSIGNED / PROCESSING / RESOLVED / CLOSED / CANCELLED */
    private String status;

    /** SLA 截止时间（创建时间 + SLA 时长） */
    private LocalDateTime slaDeadline;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}