package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.ComplianceCheck;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ComplianceCheckMapper extends BaseMapper<ComplianceCheck> {
    ComplianceCheck findByOrderNo(String orderNo);
}