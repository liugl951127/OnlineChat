package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.FaqCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * FAQ 分类 Mapper（v1.9.0）
 */
@Mapper
public interface FaqCategoryMapper extends BaseMapper<FaqCategory> {
    /** 查全部顶级分类 */
    List<FaqCategory> findAllTopLevel();

    /** 按 parentId 查子分类 */
    List<FaqCategory> findByParentId(Long parentId);
}