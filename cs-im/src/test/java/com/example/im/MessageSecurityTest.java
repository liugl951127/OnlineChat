package com.example.im;

import com.example.common.ApiException;
import com.example.common.Sanitizer;
import com.example.im.domain.ChatMessage;
import com.example.im.domain.ChatSession;
import com.example.im.repo.ChatMessageRepo;
import com.example.im.service.MessageService;
import com.example.im.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息安全 + 业务测试
 */
@SpringBootTest
class MessageSecurityTest {

    @Autowired MessageService messageService;
    @Autowired SessionService sessionService;
    @Autowired ChatMessageRepo messageRepo;

    private ChatSession setup() {
        String cid = "sec-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        return sessionService.acceptByAgent("agentS", null);
    }

    @Test
    void send_message_xssSanitized() {
        ChatSession s = setup();
        String evil = "<script>alert(1)</script> hello javascript:alert(2)";
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), evil, null);
        assertThat(m.getContent()).doesNotContain("<script>");
        assertThat(m.getContent()).doesNotContain("javascript:");
    }

    @Test
    void send_message_otherCustomer_forbidden() {
        ChatSession s = setup();
        try {
            messageService.send(s.getId(), "CUSTOMER", "another-customer", "hi", null);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(403);
        }
    }

    @Test
    void send_message_otherAgent_forbidden() {
        ChatSession s = setup();
        try {
            messageService.send(s.getId(), "AGENT", "another-agent", "hi", null);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(403);
        }
    }

    @Test
    void send_message_emptyRejected() {
        ChatSession s = setup();
        try {
            messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "  ", null);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void send_message_signed() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);
        assertThat(m.getSignature()).isNotEmpty();
        assertThat(m.getSignature()).hasSize(64);  // HMAC-SHA256 hex
    }

    @Test
    void recall_withinWindow() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "要撤回", null);
        ChatMessage recalled = messageService.recall(m.getId(), s.getCustomerId());
        assertThat(recalled.getRecalled()).isEqualTo(1);
        assertThat(recalled.getContent()).isEmpty();
        assertThat(recalled.getRecalledBy()).isEqualTo(s.getCustomerId());
    }

    @Test
    void recall_otherUser_forbidden() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);
        try {
            messageService.recall(m.getId(), "not-the-owner");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(403);
        }
    }

    @Test
    void recall_alreadyRecalled_rejected() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);
        messageService.recall(m.getId(), s.getCustomerId());
        try {
            messageService.recall(m.getId(), s.getCustomerId());
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void react_addRemoveToggle() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);

        Map<String, Object> r1 = messageService.react(m.getId(), s.getCustomerId(), "CUSTOMER", "👍");
        assertThat(r1.get("added")).isEqualTo(true);
        assertThat(((Map) r1.get("stats")).get("👍")).isEqualTo(1L);

        Map<String, Object> r2 = messageService.react(m.getId(), s.getCustomerId(), "CUSTOMER", "👍");
        assertThat(r2.get("added")).isEqualTo(false);
        assertThat(((Map) r2.get("stats")).get("👍")).isEqualTo(0L);
    }

    @Test
    void react_invalidEmoji_rejected() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);
        try {
            messageService.react(m.getId(), s.getCustomerId(), "CUSTOMER", "<script>");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void react_nonParticipant_forbidden() {
        ChatSession s = setup();
        ChatMessage m = messageService.send(s.getId(), "CUSTOMER", s.getCustomerId(), "hi", null);
        try {
            messageService.react(m.getId(), "stranger", "CUSTOMER", "👍");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(403);
        }
    }

    @Test
    void reply_crossSession_forbidden() {
        ChatSession s1 = setup();
        ChatSession s2 = setup();
        ChatMessage m1 = messageService.send(s1.getId(), "CUSTOMER", s1.getCustomerId(), "msg1", null);
        // 尝试在 s2 中回复 m1（属于 s1）
        try {
            messageService.send(s2.getId(), "CUSTOMER", s2.getCustomerId(), "reply", m1.getId());
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void sanitizer_removesControlChars() {
        String dirty = "hello\u0000world\u0007test";
        String clean = Sanitizer.text(dirty, 100);
        assertThat(clean).isEqualTo("helloworldtest");
    }

    @Test
    void sanitizer_truncates() {
        String long = "x".repeat(5000);
        String clean = Sanitizer.text(long, 100);
        assertThat(clean.length()).isEqualTo(100);
    }

    @Test
    void sanitizer_safeFileName_stripPathTraversal() {
        assertThat(Sanitizer.safeFileName("../../etc/passwd")).doesNotContain("..");
        assertThat(Sanitizer.safeFileName("../../etc/passwd")).doesNotContain("/");
    }
}