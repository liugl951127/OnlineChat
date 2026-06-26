package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String fromUser;
    private String fromRole;
    private String type;
    private String content;
    private String signature;
    private Long replyToId;
    private Integer recalled;
    private LocalDateTime recalledAt;
    private String recalledBy;
    private LocalDateTime createdAt;
}