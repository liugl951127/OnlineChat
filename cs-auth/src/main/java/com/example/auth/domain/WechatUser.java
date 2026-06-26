package com.example.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("wechat_user")
public class WechatUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerId;
    private String openid;
    private String unionid;
    private String wwUserid;
    private String username;
    /** LOCAL / WECHAT_OA / WECHAT_WORK / GITHUB / GOOGLE */
    private String provider;
    private String providerUserId;
    private String phoneEnc;
    private Integer phoneVerified;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String phoneMasked;
    private Integer loginFailCount;
    private java.time.LocalDateTime lockUntil;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}