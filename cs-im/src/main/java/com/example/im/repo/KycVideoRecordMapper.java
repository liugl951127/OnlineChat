package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.KycVideoRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * KYC 视频 Mapper（v2.0.0）
 */
@Mapper
public interface KycVideoRecordMapper extends BaseMapper<KycVideoRecord> {
    List<KycVideoRecord> findByApplicationId(Long applicationId);
}