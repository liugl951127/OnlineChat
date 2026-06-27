package com.example.message.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 离线消息持久化（MySQL 备份，Redis 主存）
 *
 * <p>消息先写 Kafka → cs-message 消费 → Redis LPUSH（在线用户投递 / 离线用户缓存）。
 * 异步任务批量同步 Redis → MySQL 做冷备，避免 Redis 重启丢失。
 */
@Data
@TableName("offline_message")
public class OfflineMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String msgId;
    private String sessionId;
    private String senderId;
    private String msgType;
    private String payload;
    private Integer delivered;
    private LocalDateTime deliveredAt;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}