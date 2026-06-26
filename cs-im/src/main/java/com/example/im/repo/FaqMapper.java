package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.Faq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * FAQ Mapper（v1.9.0）
 */
@Mapper
public interface FaqMapper extends BaseMapper<Faq> {
    /** 按分类查 */
    List<Faq> findByCategoryIdOrderByViewCountDesc(Long categoryId);

    /** 按状态查 */
    List<Faq> findByStatusOrderByViewCountDesc(String status);

    /** 全文检索（LIKE 关键词模糊） */
    List<Faq> searchByKeyword(String keyword);

    /** 增加浏览次数（原子 +1） */
    int incrementViewCount(Long id);
}