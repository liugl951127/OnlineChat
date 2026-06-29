package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户链接 (v2.3.0 坐席推送)
 *
 * <p>防 SSRF: 提交时服务端校验白名单, 黑名单过滤
 */
@Data
@TableName("chat_link")
public class ChatLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String agentUsername;
    private String targetUrl;
    private String shortToken;
    private Integer clickCount;
    private Integer maxClicks;
    private LocalDateTime expireAt;
    private Integer revoked;
    private LocalDateTime createdAt;
}