package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户账单
 */
@Data
@TableName("bill")
public class Bill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerId;
    private String bizType;
    private Double amount;
    private LocalDate bizDate;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}