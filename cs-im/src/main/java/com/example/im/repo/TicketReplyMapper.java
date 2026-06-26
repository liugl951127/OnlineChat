package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.TicketReply;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工单回复 Mapper（v1.9.0）
 */
@Mapper
public interface TicketReplyMapper extends BaseMapper<TicketReply> {
    /** 按工单 ID 查全部回复（升序） */
    List<TicketReply> findByTicketIdOrderByIdAsc(Long ticketId);
}