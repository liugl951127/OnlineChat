package com.example.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.message.domain.OfflineMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OfflineMessageMapper extends BaseMapper<OfflineMessage> {
}