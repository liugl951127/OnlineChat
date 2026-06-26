package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户身份（在网关层从 JWT 解析后写入 Header，下游服务读取）
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class SecurityContext {
    /** CUSTOMER / AGENT / ADMIN */
    private String role;
    private String userId;
    private String displayName;
    /** WECHAT_OA / WECHAT_WORK / LOCAL */
    private String channel;
    /** AGENT 才有；CUSTOMER 为 null */
    private String skills;
    /** ADMIN 才有 */
    private String adminLevel;
}