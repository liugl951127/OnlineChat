package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户持仓（订单 SETTLED 后写入）
 */
@Data
@TableName("holding")
public class Holding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerId;
    private String productCode;
    private Double principal;
    private Double accumulatedIncome;
    /** 持仓状态：HOLDING / REDEEMED */
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic

    private Integer deleted;
}