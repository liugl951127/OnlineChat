package com.example.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 统一用户表（多种登录方式：公众号 / 企微 / 用户名密码 / 手机号）
 */
@Entity
@Table(name = "wechat_user", indexes = {
        @Index(name = "idx_wu_openid", columnList = "openid"),
        @Index(name = "idx_wu_userid", columnList = "wwUserId"),
        @Index(name = "idx_wu_username", columnList = "username"),
        @Index(name = "idx_wu_phone", columnList = "phone")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WechatUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CUSTOMER 内部 ID（不可变） */
    @Column(nullable = false, unique = true, length = 64)
    private String customerId;

    /** OA 公众号 openid */
    @Column(length = 64)
    private String openid;

    /** OA unionid（跨公众号） */
    @Column(length = 64)
    private String unionid;

    /** 企微 userid */
    @Column(length = 64)
    private String wwUserid;

    /** 用户名（账号密码登录） */
    @Column(length = 64)
    private String username;

    /** OAuth 提供方：GITHUB / GOOGLE / WECHAT_OA / WECHAT_WORK / LOCAL */
    @Column(length = 32)
    private String provider;

    /** OAuth 提供方的用户唯一 ID */
    @Column(length = 128)
    private String providerUserId;

    /** 手机号（AES 加密存储） */
    @Column(length = 128)
    private String phoneEnc;

    /** 手机号是否已验证 */
    @Column
    private Integer phoneVerified;

    /** BCrypt 哈希后的密码（NULL 表示未设置密码，只能走其他登录方式） */
    @Column(length = 128)
    private String passwordHash;

    /** 昵称 */
    @Column(length = 64)
    private String nickname;

    /** 头像 */
    @Column(length = 512)
    private String avatar;

    /** 手机号脱敏（显示用） */
    @Column(length = 32)
    private String phoneMasked;

    /** 连续登录失败次数（用于锁定） */
    @Column
    private Integer loginFailCount;

    /** 锁定到期时间 */
    @Column
    private Instant lockUntil;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
        if (loginFailCount == null) loginFailCount = 0;
        if (phoneVerified == null) phoneVerified = 0;
    }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}