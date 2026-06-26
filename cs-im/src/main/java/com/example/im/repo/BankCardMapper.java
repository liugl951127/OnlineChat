package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.BankCard;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 银行卡 Mapper（v2.0.0）
 */
@Mapper
public interface BankCardMapper extends BaseMapper<BankCard> {

    /** 按客户查全部 */
    List<BankCard> findByCustomerIdOrderByIsDefaultDesc(String customerId);

    /** 查客户的默认卡 */
    Optional<BankCard> findDefaultByCustomerId(String customerId);

    /** 按卡号查 */
    Optional<BankCard> findByCardNoEnc(String cardNoEnc);
}