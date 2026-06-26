package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.RiskAssessment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RiskAssessmentMapper extends BaseMapper<RiskAssessment> {
    RiskAssessment findLatestByCustomerId(String customerId);
}