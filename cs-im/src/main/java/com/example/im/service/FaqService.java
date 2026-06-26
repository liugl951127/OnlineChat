package com.example.im.service;

import com.example.im.domain.Faq;
import com.example.im.domain.FaqCategory;
import com.example.im.repo.FaqCategoryMapper;
import com.example.im.repo.FaqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FAQ 知识库业务（v1.9.0）
 *
 * <p>机器人应答集成：在 RobotEngine 中检测关键词，未命中时调 {@link #search} 找 FAQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqMapper faqMapper;
    private final FaqCategoryMapper categoryMapper;

    /**
     * 搜索 FAQ（按关键词）
     *
     * @param keyword 用户问题或关键词
     * @return 命中的 FAQ 列表（按 view_count 降序）
     */
    public List<Faq> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return faqMapper.searchByKeyword(keyword);
    }

    /**
     * 浏览（view_count +1）
     */
    public void incrementView(Long id) {
        faqMapper.incrementViewCount(id);
    }

    /**
     * 查分类下的 FAQ
     */
    public List<Faq> listByCategory(Long categoryId) {
        return faqMapper.findByCategoryIdOrderByViewCountDesc(categoryId);
    }

    /**
     * 热门 FAQ
     */
    public List<Faq> topFaqs() {
        return faqMapper.findByStatusOrderByViewCountDesc("PUBLISHED");
    }

    /**
     * 全部顶级分类
     */
    public List<FaqCategory> topCategories() {
        return categoryMapper.findAllTopLevel();
    }

    /**
     * 子分类
     */
    public List<FaqCategory> subCategories(Long parentId) {
        return categoryMapper.findByParentId(parentId);
    }

    /**
     * 创建 FAQ（管理员）
     */
    public Faq create(Long categoryId, String question, String answer, String keywords) {
        Faq f = new Faq();
        f.setCategoryId(categoryId);
        f.setQuestion(question);
        f.setAnswer(answer);
        f.setKeywords(keywords);
        f.setStatus("PUBLISHED");
        f.setViewCount(0);
        f.setHelpfulCount(0);
        f.setUnhelpfulCount(0);
        faqMapper.insert(f);
        return f;
    }
}