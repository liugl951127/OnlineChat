package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    @Select("SELECT * FROM contract WHERE contract_no = #{contractNo} AND deleted = 0 LIMIT 1")
    Contract findByContractNo(String contractNo);

    @Select("SELECT * FROM contract WHERE order_no = #{orderNo} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    Contract findByOrderNo(String orderNo);
}