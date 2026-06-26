package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.Product;
import com.example.im.repo.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductMapper productMapper;

    /** 全部在售产品 */
    @GetMapping("/list")
    public ApiResponse<List<Product>> list(@RequestParam(required = false) String type) {
        List<Product> list = (type == null)
            ? productMapper.findByStatus("ON_SALE")
            : productMapper.findByProductType(type);
        return ApiResponse.ok(list);
    }

    /** 详情 */
    @GetMapping("/{code}")
    public ApiResponse<Product> detail(@PathVariable String code) {
        Product p = productMapper.findByCode(code);
        if (p == null) return ApiResponse.fail(404, "产品不存在");
        return ApiResponse.ok(p);
    }
}