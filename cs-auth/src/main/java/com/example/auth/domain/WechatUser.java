package com.example.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 微信用户（含公众号 openid + 企微 userid 同一张表以 type 区分）
 */
@Entity
@Table(name = "wechat_user", indexes = {
        @Index(name = "idx_wu_openid", columnList = "openid"),
        @Index(name = "idx_wu_userid", columnList = "wwUserId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WechatUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CUSTOMER 内部 ID（生成后不可变） */
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

    /** 昵称（脱敏后存） */
    @Column(length = 64)
    private String nickname;

    /** 头像 URL */
    @Column(length = 512)
    private String avatar;

    /** 手机号（脱敏） */
    @Column(length = 32)
    private String phoneMasked;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}