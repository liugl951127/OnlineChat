package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.KycRiskStatement;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 风险声明 Mapper（v2.0.0）
 */
@Mapper
public interface KycRiskStatementMapper extends BaseMapper<KycRiskStatement> {

    /** 按状态查（前端朗读页面用） */
    List<KycRiskStatement> findByStatusOrderBySortOrderAsc(String status);

    /** 按分类查 */
    List<KycRiskStatement> findByCategoryOrderBySortOrderAsc(String category);

    /** 按代码查 */
    KycRiskStatement findByCode(String code);
}