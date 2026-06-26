package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.Ticket;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 工单 Mapper（v1.9.0）
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    /** 按工单号查 */
    Optional<Ticket> findByTicketNo(String ticketNo);

    /** 按客户 ID 查全部 */
    List<Ticket> findByCustomerIdOrderByIdDesc(String customerId);

    /** 按坐席查全部（已分配或处理的） */
    List<Ticket> findByAgentUsernameOrderByIdDesc(String agentUsername);

    /** 按状态查 */
    List<Ticket> findByStatusOrderByPriorityDesc(String status);
}