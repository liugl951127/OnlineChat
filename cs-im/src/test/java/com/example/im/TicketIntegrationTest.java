package com.example.im;

import com.example.im.domain.Faq;
import com.example.im.domain.FaqCategory;
import com.example.im.domain.Ticket;
import com.example.im.domain.TicketReply;
import com.example.im.service.FaqService;
import com.example.im.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工单 + FAQ + 回放 集成测试（v1.9.0）
 * 真实 MySQL + Redis + Kafka 环境
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class TicketIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private FaqService faqService;

    @Test
    void testTicketFullFlow() {
        // 1) 创建工单
        Ticket t = ticketService.create("c-ticket-001", "测试问题",
                "详细描述", "GENERAL", "HIGH", null);
        assertNotNull(t.getTicketNo());
        assertEquals("OPEN", t.getStatus());
        assertEquals("HIGH", t.getPriority());

        // 2) 分配工单
        t = ticketService.assign(t.getTicketNo(), "agent01");
        assertEquals("ASSIGNED", t.getStatus());
        assertEquals("agent01", t.getAgentUsername());

        // 3) 开始处理
        t = ticketService.startProcessing(t.getTicketNo());
        assertEquals("PROCESSING", t.getStatus());

        // 4) 客户回复
        TicketReply reply = ticketService.reply(t.getId(), "c-ticket-001", "CUSTOMER", "补充信息", null);
        assertNotNull(reply.getId());
        assertEquals("CUSTOMER", reply.getFromRole());

        // 5) 标记解决
        t = ticketService.resolve(t.getTicketNo());
        assertEquals("RESOLVED", t.getStatus());
        assertNotNull(t.getResolvedAt());

        // 6) 关闭
        t = ticketService.close(t.getTicketNo());
        assertEquals("CLOSED", t.getStatus());
        assertNotNull(t.getClosedAt());

        // 7) 查回复
        List<TicketReply> replies = ticketService.listReplies(t.getId());
        assertEquals(1, replies.size());
    }

    @Test
    void testTicketCancel() {
        Ticket t = ticketService.create("c-ticket-cancel", "要取消的工单",
                "test", "GENERAL", "LOW", null);
        t = ticketService.cancel(t.getTicketNo(), "客户主动取消");
        assertEquals("CANCELLED", t.getStatus());
    }

    @Test
    void testFaqSearch() {
        // 创建分类
        // 创建 FAQ
        Faq f = faqService.create(1L, "如何开通账户", "请在 App 中点击'我的'->'开通账户'", "开户,注册,账户");
        assertNotNull(f.getId());
        assertEquals("PUBLISHED", f.getStatus());

        // 搜索
        List<Faq> results = faqService.search("开户");
        assertFalse(results.isEmpty());

        // 浏览 +1
        faqService.incrementView(f.getId());
    }

    @Test
    void testFaqTop() {
        List<Faq> top = faqService.topFaqs();
        assertNotNull(top);
    }
}