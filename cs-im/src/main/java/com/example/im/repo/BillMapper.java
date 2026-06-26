package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.Bill;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {
    List<Bill> findByCustomerIdAndBizDateGreaterThanEqualOrderByBizDateDesc(String customerId, LocalDate from);
}