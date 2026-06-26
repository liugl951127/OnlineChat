package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;       // MyBatis Plus ID 策略
import com.baomidou.mybatisplus.annotation.TableField;  // 字段注解（fill / logic 等）
import com.baomidou.mybatisplus.annotation.TableId;     // 主键注解
import com.baomidou.mybatisplus.annotation.TableLogic;  // 逻辑删除
import com.baomidou.mybatisplus.annotation.TableName;  // 表名映射
import lombok.Data;                                      // Lombok 自动 getter/setter/toString

import java.time.LocalDateTime;                          // JDK 8 时间类型

/**
 * 聊天消息实体（cs_im.chat_message 表）
 *
 * <p>v1.9.0 增强：
 * <ul>
 *   <li>deleted 字段：MyBatis Plus 逻辑删除</li>
 *   <li>video_frame_url：可选的截图帧（用于视频回溯）</li>
 * </ul>
 */
@Data
@TableName("chat_message")                            // 表名映射
public class ChatMessage {
    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID（关联 chat_session.id） */
    private Long sessionId;

    /** 发送方 ID（customerId / agentUsername / "SYSTEM" / "ROBOT"） */
    private String fromUser;

    /** 发送方角色：CUSTOMER / AGENT / ROBOT / SYSTEM */
    private String fromRole;

    /** 消息类型：TEXT / IMAGE / FILE / RICH / VIDEO */
    private String type;

    /** 消息内容（已 XSS 净化） */
    private String content;

    /** 消息签名（防篡改） */
    private String signature;

    /** 引用的消息 ID（可选） */
    private Long replyToId;

    /** 是否已撤回（0/1） */
    private Integer recalled;

    /** 撤回时间 */
    private LocalDateTime recalledAt;

    /** 撤回人 */
    private String recalledBy;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除（0/1） */
    @TableLogic
    private Integer deleted;

    /** 视频回溯帧截图 URL（可选，用于视频回溯功能） */
    private String videoFrameUrl;
}