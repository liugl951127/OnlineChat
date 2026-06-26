package com.example.im.repo;

import com.example.im.domain.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReactionRepo extends JpaRepository<MessageReaction, Long> {
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);
    List<MessageReaction> findByMessageIdIn(List<Long> messageIds);
    List<MessageReaction> findByMessageId(Long messageId);
    void deleteByMessageIdAndUserIdAndEmoji(Long messageId, String userId, String emoji);
}