package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 推荐反馈 — 用于模型调优
 *
 * @author MiniMax
 */
@Data
@TableName("ai_feedback")
public class AiFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long suggestionId;
    private String agentUsername;

    /** USED / SKIPPED / MODIFIED / RATED */
    private String feedbackType;

    private Integer rating;

    /** 坐席修改后的内容（MODIFIED 时填） */
    private String modifiedContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}