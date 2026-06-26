package com.example.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解（合规：标注后由切面统一写审计表/文件）
 * 用于：登录、转账、黑名单变更、敏感词命中、人工坐席变更等
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 操作名：LOGIN / TRANSFER / BLACKLIST_ADD / SENSITIVE_HIT / SESSION_HANGUP ... */
    String action();
    /** 操作对象类型：CUSTOMER / AGENT / SESSION / WORD ... */
    String targetType() default "";
    /** 是否记录请求参数（合规默认 true，敏感字段需配合 SensitiveUtils） */
    boolean recordArgs() default true;
}