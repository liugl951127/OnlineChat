package com.example.auth.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.domain.WechatUser;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface WechatUserMapper extends BaseMapper<WechatUser> {
    Optional<WechatUser> findByCustomerId(String customerId);
    Optional<WechatUser> findByUsername(String username);
    Optional<WechatUser> findByPhoneEnc(String phoneEnc);
    Optional<WechatUser> findByProviderAndProviderUserId(String provider, String providerUserId);
}