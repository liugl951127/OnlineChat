package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.FinancialOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinancialOrderMapper extends BaseMapper<FinancialOrder> {
    FinancialOrder findByOrderNo(String orderNo);
    List<FinancialOrder> findByCustomerIdOrderByIdDesc(String customerId);
    List<FinancialOrder> findByStatus(String status);
}