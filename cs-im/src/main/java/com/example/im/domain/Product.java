package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金融产品（保险/理财/基金）
 *
 * <p>合并原 cs-trade 的 Product，扩展支持金融产品分类。
 */
@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String name;
    private String description;
    /** 产品类型：INSURANCE / DEPOSIT / FUND / BOND */
    private String productType;
    /** 风险等级：LOW / MID / HIGH */
    private String riskLevel;
    /** 年化收益率（%），保险产品无收益时为 0 */
    private Double yieldRate;
    /** 期限（如 30D/90D/1Y/PERPETUAL=活期） */
    private String period;
    /** 起购金额（元） */
    private Double minAmount;
    /** 限购金额（元，0=不限） */
    private Double maxAmount;
    /** 销售状态：ON_SALE / SUSPENDED / OFF_SHELF */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 逻辑删除标记 (0=未删, 1=已删) */

    @TableLogic

    private Integer deleted;
}