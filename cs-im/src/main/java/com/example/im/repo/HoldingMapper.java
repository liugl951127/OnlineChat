package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.Holding;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HoldingMapper extends BaseMapper<Holding> {
    List<Holding> findByCustomerId(String customerId);
    List<Holding> findByCustomerIdAndStatus(String customerId, String status);
}