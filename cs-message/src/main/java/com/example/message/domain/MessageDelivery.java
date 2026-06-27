package com.example.message.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息投递日志（每个用户的每次投递尝试记一条）
 */
@Data
@TableName("message_delivery")
public class MessageDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String msgId;
    private String userId;
    private String channel;
    private String status;
    private Integer retryCount;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime sentAt;

    private LocalDateTime ackedAt;
}