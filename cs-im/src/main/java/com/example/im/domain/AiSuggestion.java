package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 推荐话术 — 坐席 AI 助手核心表
 *
 * <p>工作流：
 * <ol>
 *   <li>客户发送消息 → AiAssistantService 异步生成推荐</li>
 *   <li>推荐通过 WebSocket 推送给坐席 (/user/queue/ai-suggestion)</li>
 *   <li>坐席点击"采纳"/"修改"/"忽略"</li>
 *   <li>反馈写入 ai_feedback（用于后续模型调优）</li>
 * </ol>
 *
 * @author MiniMax
 */
@Data
@TableName("ai_suggestion")
public class AiSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 客户 ID */
    private String customerId;

    /** 坐席用户名 */
    private String agentUsername;

    /** 触发的客户消息 ID */
    private Long triggerMessageId;

    /** 推荐类型：REPLY（话术）/ KNOWLEDGE（知识库）/ FAQ（常见问题）/ ACTION（操作建议） */
    private String suggestionType;

    /** 推荐内容 */
    private String content;

    /** 置信度 0-100 */
    private Double confidence;

    /** 引用来源（faq_id/knowledge_id，逗号分隔） */
    private String sources;

    /** 是否被坐席采纳 */
    private Boolean used;

    /** 坐席评分 1-5 */
    private Integer rating;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}