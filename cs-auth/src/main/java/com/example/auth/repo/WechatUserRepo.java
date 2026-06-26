package com.example.auth.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.auth.domain.WechatUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class WechatUserRepo {

    @Autowired private WechatUserMapper mapper;

    public WechatUser save(WechatUser u) {
        if (u.getId() == null) {
            mapper.insert(u);
        } else {
            mapper.updateById(u);
        }
        return u;
    }

    public Optional<WechatUser> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<WechatUser> findByCustomerId(String customerId) {
        return mapper.findByCustomerId(customerId);
    }

    public Optional<WechatUser> findByUsername(String username) {
        return mapper.findByUsername(username);
    }

    public Optional<WechatUser> findByPhoneEnc(String phoneEnc) {
        return mapper.findByPhoneEnc(phoneEnc);
    }

    public Optional<WechatUser> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return mapper.findByProviderAndProviderUserId(provider, providerUserId);
    }

    /** 兼容旧 JPA 方法（MyBatis Plus 派生查询） */
    public Optional<WechatUser> findByOpenid(String openid) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<WechatUser>().eq("openid", openid)));
    }

    public Optional<WechatUser> findByWwUserid(String wwUserid) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<WechatUser>().eq("ww_userid", wwUserid)));
    }

    public boolean existsByUsername(String username) {
        return mapper.selectCount(new QueryWrapper<WechatUser>().eq("username", username)) > 0;
    }

    public boolean existsByPhoneEnc(String phoneEnc) {
        return mapper.selectCount(new QueryWrapper<WechatUser>().eq("phone_enc", phoneEnc)) > 0;
    }
}