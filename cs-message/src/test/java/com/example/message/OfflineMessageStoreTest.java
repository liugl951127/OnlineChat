package com.example.message;

import com.example.common.kafka.ChatMessageEvent;
import com.example.message.service.OfflineMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线消息存储测试（用 Redis 真实连接，如不可用则跳过）
 */
@SpringBootTest
class OfflineMessageStoreTest {

    @Autowired OfflineMessageStore offlineStore;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void cleanup() {
        try { redis.delete(OfflineMessageStore.key("test-user")); } catch (Exception ignored) {}
    }

    @Test
    void pushAndDrain() {
        ChatMessageEvent e1 = ChatMessageEvent.builder().msgId("m1").toId("u1").content("hello").build();
        ChatMessageEvent e2 = ChatMessageEvent.builder().msgId("m2").toId("u1").content("world").build();
        offlineStore.push("test-user", e1);
        offlineStore.push("test-user", e2);

        assertThat(offlineStore.size("test-user")).isEqualTo(2);

        List<ChatMessageEvent> drained = offlineStore.drain("test-user");
        assertThat(drained).hasSize(2);
        // drain 后清空
        assertThat(offlineStore.size("test-user")).isEqualTo(0);
    }

    @Test
    void pushBatch() {
        List<ChatMessageEvent> batch = List.of(
            ChatMessageEvent.builder().msgId("a").content("1").build(),
            ChatMessageEvent.builder().msgId("b").content("2").build(),
            ChatMessageEvent.builder().msgId("c").content("3").build()
        );
        offlineStore.pushBatch("test-user", batch);
        assertThat(offlineStore.size("test-user")).isEqualTo(3);
    }

    @Test
    void peek_doesNotConsume() {
        offlineStore.push("test-user", ChatMessageEvent.builder().msgId("x").build());
        List<ChatMessageEvent> peeked = offlineStore.peek("test-user", 10);
        assertThat(peeked).hasSize(1);
        // peek 后仍存在
        assertThat(offlineStore.size("test-user")).isEqualTo(1);
    }

    @Test
    void trimMaxSize() {
        // 推 200 条（超过 maxMessages=100）
        for (int i = 0; i < 200; i++) {
            offlineStore.push("test-user", ChatMessageEvent.builder().msgId("m" + i).build());
        }
        // 只保留最新 100 条
        assertThat(offlineStore.size("test-user")).isLessThanOrEqualTo(100);
    }

    @Test
    void emptyUser_returnsEmpty() {
        List<ChatMessageEvent> drained = offlineStore.drain("nonexistent-user");
        assertThat(drained).isEmpty();
    }
}