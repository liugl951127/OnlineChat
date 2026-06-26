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
 * FAQ 问答（cs_im.faq 表）
 *
 * <p>v1.9.0 知识库核心模型。
 *
 * <p>字段：
 * <ul>
 *   <li>question：问题</li>
 *   <li>answer：答案（支持 HTML）</li>
 *   <li>keywords：逗号分隔的关键词（用于全文检索）</li>
 *   <li>view_count：浏览次数</li>
 *   <li>helpful_count / unhelpful_count：用户反馈</li>
 *   <li>status：DRAFT / PUBLISHED / ARCHIVED</li>
 * </ul>
 */
@Data
@TableName("faq")
public class Faq {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类 ID */
    private Long categoryId;

    /** 问题 */
    private String question;

    /** 答案（HTML/Markdown） */
    private String answer;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 浏览次数 */
    private Integer viewCount;

    /** 有用计数 */
    private Integer helpfulCount;

    /** 无用计数 */
    private Integer unhelpfulCount;

    /** 状态：DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}