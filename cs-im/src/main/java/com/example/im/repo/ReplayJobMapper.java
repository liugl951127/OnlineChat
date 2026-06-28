package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.ReplayJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplayJobMapper extends BaseMapper<ReplayJob> {
    List<ReplayJob> findBySession(@Param("sessionId") Long sessionId);
    ReplayJob findLatestBySession(@Param("sessionId") Long sessionId);
    ReplayJob findById(@Param("id") Long id);
}