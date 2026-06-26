package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.im.domain.Faq;
import com.example.im.domain.FaqCategory;
import com.example.im.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FAQ REST 接口（v1.9.0）
 */
@RestController
@RequestMapping("/im/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    /** 搜索 FAQ */
    @GetMapping("/search")
    public ApiResponse<List<Faq>> search(@RequestParam String keyword) {
        return ApiResponse.ok(faqService.search(keyword));
    }

    /** 热门 FAQ */
    @GetMapping("/top")
    public ApiResponse<List<Faq>> top() {
        return ApiResponse.ok(faqService.topFaqs());
    }

    /** 分类下 FAQ */
    @GetMapping("/category/{categoryId}")
    public ApiResponse<List<Faq>> byCategory(@PathVariable Long categoryId) {
        return ApiResponse.ok(faqService.listByCategory(categoryId));
    }

    /** 顶级分类 */
    @GetMapping("/category/top")
    public ApiResponse<List<FaqCategory>> topCategories() {
        return ApiResponse.ok(faqService.topCategories());
    }

    /** 子分类 */
    @GetMapping("/category/{parentId}/children")
    public ApiResponse<List<FaqCategory>> subCategories(@PathVariable Long parentId) {
        return ApiResponse.ok(faqService.subCategories(parentId));
    }

    /** 浏览 +1 */
    @PostMapping("/{id}/view")
    public ApiResponse<Boolean> view(@PathVariable Long id) {
        faqService.incrementView(id);
        return ApiResponse.ok(true);
    }

    /** 创建 FAQ（管理员） */
    @PostMapping("/create")
    public ApiResponse<Faq> create(@RequestBody Faq req) {
        if (req.getQuestion() == null || req.getAnswer() == null) {
            throw new ApiException(400, "question/answer 必填");
        }
        return ApiResponse.ok(faqService.create(
                req.getCategoryId(), req.getQuestion(), req.getAnswer(), req.getKeywords()));
    }
}