package com.example.im.repo;

import com.example.im.domain.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ChatMessageRepo {

    @Autowired private ChatMessageMapper mapper;

    public ChatMessage save(ChatMessage m) {
        mapper.insert(m);
        return m;
    }

    public int insert(ChatMessage m) { return mapper.insert(m); }

    public Optional<ChatMessage> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId) {
        return mapper.findBySessionIdOrderByIdAsc(sessionId);
    }

    public int updateById(ChatMessage m) {
        return mapper.updateById(m);
    }
}