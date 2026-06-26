package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单回复（cs_im.ticket_reply 表）
 *
 * <p>客户 ↔ 坐席在工单下的多轮对话，支持附件。
 */
@Data
@TableName("ticket_reply")
public class TicketReply {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 ID */
    private Long ticketId;

    /** 回复人（customerId / agentUsername / "SYSTEM"） */
    private String fromUser;

    /** 回复人角色：CUSTOMER / AGENT / SYSTEM */
    private String fromRole;

    /** 回复内容 */
    private String content;

    /** 附件 URL（可空） */
    private String attachmentUrl;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}