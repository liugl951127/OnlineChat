package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库 — RAG 检索增强
 *
 * <p>三类知识：
 * <ul>
 *   <li>PRODUCT — 产品说明（理财产品/基金/保险）</li>
 *   <li>POLICY — 监管政策（KYC/SLA/合规）</li>
 *   <li>FAQ — 常见问题（操作类）</li>
 * </ul>
 *
 * @author MiniMax
 */
@Data
@TableName("ai_knowledge")
public class AiKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;

    /** GENERAL / PRODUCT / POLICY / FAQ */
    private String category;

    /** 标签（逗号分隔） */
    private String tags;

    /** 向量化（768 维 float[]） */
    private byte[] embedding;

    /** embedding 模型标识 */
    private String embeddingModel;

    private Integer viewCount;
    private Integer helpfulCount;

    /** MANUAL / FAQ_SYNC / IMPORTED */
    private String source;

    /** 关联源 ID（如 FAQ ID） */
    private Long sourceId;

    /** ACTIVE / ARCHIVED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}