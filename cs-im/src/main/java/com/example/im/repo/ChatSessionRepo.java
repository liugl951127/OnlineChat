package com.example.im.repo;

import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ChatSession 仓储（MyBatis Plus 包装）
 */
@Repository
public class ChatSessionRepo {

    @Autowired private ChatSessionMapper mapper;

    public ChatSession save(ChatSession s) {
        mapper.insert(s);
        return s;
    }

    public int insert(ChatSession s) { return mapper.insert(s); }

    public Optional<ChatSession> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<ChatSession> findAll() {
        return mapper.selectList(null);
    }

    public int updateById(ChatSession s) {
        return mapper.updateById(s);
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    /** 查客户活跃会话 (ROBOT/QUEUED/IN_SESSION/TRADE_CHECKING) */
    public Optional<ChatSession> findActiveByCustomer(String customerId) {
        return mapper.findActiveByCustomer(customerId);
    }

    /** 按状态查 */
    public List<ChatSession> findByStatus(SessionStatus status) {
        return mapper.findByStatus(status.name());
    }

    /** 按 customerId 查 */
    public List<ChatSession> findByCustomerId(String customerId) {
        return mapper.findByCustomerId(customerId);
    }

    /** 按 agentUsername 查 */
    public List<ChatSession> findByAgentUsername(String agentUsername) {
        return mapper.findByAgentUsername(agentUsername);
    }
}