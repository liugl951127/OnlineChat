package com.example.auth.repo;

import com.example.auth.domain.WechatUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WechatUserRepo extends JpaRepository<WechatUser, Long> {
    Optional<WechatUser> findByOpenid(String openid);
    Optional<WechatUser> findByUnionid(String unionid);
    Optional<WechatUser> findByWwUserid(String wwUserid);
    Optional<WechatUser> findByCustomerId(String customerId);
}