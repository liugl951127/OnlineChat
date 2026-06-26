package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.KycApplication;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * KYC 申请单 Mapper（v2.0.0）
 */
@Mapper
public interface KycApplicationMapper extends BaseMapper<KycApplication> {

    /** 按申请单号查 */
    Optional<KycApplication> findByApplicationNo(String applicationNo);

    /** 查客户的最新申请（按 ID 降序） */
    Optional<KycApplication> findLatestByCustomerId(String customerId);

    /** 按状态查（审核工作台） */
    List<KycApplication> findByStatusOrderByIdDesc(String status);

    /** 按审核员查已审核 */
    List<KycApplication> findByAuditorUsernameOrderByIdDesc(String auditorUsername);
}