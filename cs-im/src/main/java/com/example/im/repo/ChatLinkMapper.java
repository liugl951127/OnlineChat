package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.ChatLink;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatLinkMapper extends BaseMapper<ChatLink> {
    ChatLink findFirstByToken(String shortToken);
}