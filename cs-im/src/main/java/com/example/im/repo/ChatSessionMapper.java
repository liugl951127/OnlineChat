package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    Optional<ChatSession> findActiveByCustomer(String customerId);
    List<ChatSession> findByStatus(String status);
    List<ChatSession> findByCustomerId(String customerId);
    List<ChatSession> findByAgentUsername(String agentUsername);
}