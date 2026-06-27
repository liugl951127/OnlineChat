package com.example.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.message.domain.MessageDelivery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageDeliveryMapper extends BaseMapper<MessageDelivery> {
}