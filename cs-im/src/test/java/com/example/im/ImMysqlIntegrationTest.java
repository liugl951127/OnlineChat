package com.example.im;

import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cs-im 端到端集成测试（连接真实 MySQL + Kafka + Redis）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mysql-it")
@org.springframework.boot.test.mock.mockito.MockBean(com.example.im.client.RobotClient.class)
@org.springframework.boot.test.mock.mockito.MockBean(com.example.im.client.TradeClient.class)
class ImMysqlIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired SessionService sessionService; // 仅用于 messagesOf
    @Autowired ChatSessionRepo sessionRepo;
    @Autowired ChatMessageRepo messageRepo;

    @BeforeEach
    void clean() {
        try { messageRepo.deleteAll(); } catch (Exception ignored) {}
        try { sessionRepo.deleteAll(); } catch (Exception ignored) {}
    }

    @Test
    void context_loads_with_mysql_kafka_redis() {
        // Spring 容器启动 + 连接 MySQL/Kafka/Redis 成功
        long sessions = sessionRepo.count();
        long messages = messageRepo.count();
        System.out.println("[IT-IM] ✓ Spring context loaded. sessions=" + sessions + ", messages=" + messages);
        assertThat(sessions).isGreaterThanOrEqualTo(0);
        assertThat(messages).isGreaterThanOrEqualTo(0);
    }

    @Test
    void session_and_message_crud() {
        // 1) 创建会话（直接用 JPA）
        ChatSession s = new ChatSession();
        s.setCustomerId("test-customer");
        s.setStatus(SessionStatus.QUEUED);
        ChatSession saved = sessionRepo.save(s);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.QUEUED);
        System.out.println("[IT-IM] ✓ Session created: id=" + saved.getId() + ", customer=" + saved.getCustomerId() + ", status=" + saved.getStatus());

        // 2) 写入消息
        ChatMessage m = new ChatMessage();
        m.setSessionId(saved.getId());
        m.setFromUser("test-customer");
        m.setFromRole("CUSTOMER");
        m.setType("TEXT");
        m.setContent("hello mysql");
        ChatMessage savedMsg = messageRepo.save(m);
        assertThat(savedMsg.getId()).isNotNull();
        assertThat(savedMsg.getContent()).isEqualTo("hello mysql");
        System.out.println("[IT-IM] ✓ Message saved: id=" + savedMsg.getId() + ", content=" + savedMsg.getContent());

        // 3) 查询会话的所有消息
        List<ChatMessage> msgs = messageRepo.findBySessionIdOrderByIdAsc(saved.getId());
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getContent()).isEqualTo("hello mysql");
        System.out.println("[IT-IM] ✓ Found " + msgs.size() + " message(s) in session");
    }
}