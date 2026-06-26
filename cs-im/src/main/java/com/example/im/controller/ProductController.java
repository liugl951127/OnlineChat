package com.example.im.controller;

import com.example.common.ApiException;                  // 业务异常
import com.example.common.ApiResponse;                  // 统一返回
import com.example.im.domain.Product;                    // 产品实体
import com.example.im.repo.ProductMapper;                // 产品 Mapper
import lombok.RequiredArgsConstructor;                   // Lombok 注入
import org.springframework.web.bind.annotation.*;        // Spring MVC 注解

import java.util.List;                                    // 列表返回

/**
 * 金融产品 REST 接口（v1.8.0 / v1.9.0）
 *
 * <p>客户端：
 * <ul>
 *   <li>GET /product/list?type=DEPOSIT — 在售产品列表</li>
 *   <li>GET /product/{code} — 产品详情</li>
 * </ul>
 */
@RestController                                          // REST 控制器
@RequestMapping("/product")                              // 一级路由
@RequiredArgsConstructor                                // 自动注入
public class ProductController {

    /** 产品 Mapper（MyBatis Plus） */
    private final ProductMapper productMapper;

    /**
     * 在售产品列表（可按 type 过滤）
     *
     * @param type 可选：DEPOSIT / FUND / BOND / INSURANCE
     * @return 产品列表
     */
    @GetMapping("/list")
    public ApiResponse<List<Product>> list(@RequestParam(required = false) String type) {
        // 有 type 参数则按类型过滤；否则查全部 ON_SALE
        List<Product> list = (type == null)
                ? productMapper.findByStatus("ON_SALE")
                : productMapper.findByProductType(type);
        return ApiResponse.ok(list);
    }

    /**
     * 产品详情
     *
     * @param code 产品代码（如 PRD-DEP-001）
     * @return 产品详情
     */
    @GetMapping("/{code}")
    public ApiResponse<Product> detail(@PathVariable String code) {
        // 查产品
        Product p = productMapper.findByCode(code);
        if (p == null) throw new ApiException(404, "产品不存在");
        return ApiResponse.ok(p);
    }
}