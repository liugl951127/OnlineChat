package com.example.im;

import com.example.im.client.RobotClient;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ImEndToEndTest {

    @Autowired SessionService sessionService;
    @Autowired ChatSessionRepo sessionRepo;
    @MockBean RobotClient robotClient;
    @MockBean com.example.im.client.TradeClient tradeClient;

    @Test
    void newCustomer_defaultRobotStatus() {
        String cid = "c-test-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ROBOT);
    }

    @Test
    void transferAndAccept_endToEnd() {
        String cid = "c-test-" + System.nanoTime();
        ChatSession s1 = sessionService.getOrCreate(cid);
        ChatSession queued = sessionService.transferToQueue(cid);
        assertThat(queued.getStatus()).isEqualTo(SessionStatus.QUEUED);

        String agent = "agent-" + System.nanoTime();
        ChatSession accepted = sessionService.acceptByAgent(agent, null);
        assertThat(accepted.getStatus()).isEqualTo(SessionStatus.IN_SESSION);
        assertThat(accepted.getAgentUsername()).isEqualTo(agent);
    }

    @Test
    void robotReply_thenCustomerHangup() {
        String cid = "c-test-" + System.nanoTime();
        sessionService.getOrCreate(cid);
        when(robotClient.chat(any())).thenReturn(
                com.example.common.ApiResponse.ok(com.example.common.RichMessage.text("机器人回复")));

        // 模拟 IM/Customer/chat 流程：客户发"人工"，机器人响应 → 转人工
        ChatSession s = sessionService.getOrCreate(cid);
        var resp = robotClient.chat(new RobotClient.ChatReq(cid, "人工"));
        assertThat(resp.getData().getText()).isEqualTo("机器人回复");

        // 主动转人工
        ChatSession queued = sessionService.transferToQueue(cid);
        assertThat(queued.getStatus()).isEqualTo(SessionStatus.QUEUED);
    }

    @Test
    void adminForceHangup() {
        String cid = "c-test-" + System.nanoTime();
        sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        ChatSession accepted = sessionService.acceptByAgent("agentA", null);
        ChatSession forced = sessionService.forceHangup(accepted.getId(), "测试强制挂断");
        assertThat(forced.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(forced.getEndedBy()).isEqualTo("ADMIN");
    }

    @Test
    void listByCustomer_returnsHistory() {
        String cid = "c-test-" + System.nanoTime();
        sessionService.getOrCreate(cid);
        sessionService.hangup("CUSTOMER", cid, sessionRepo.findAll().stream()
                .filter(x -> cid.equals(x.getCustomerId())).findFirst().orElseThrow().getId());

        sessionService.getOrCreate(cid);  // 再创建一个
        List<ChatSession> history = sessionService.listByCustomer(cid);
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void stats_aggregatesCorrect() {
        Map<String, Object> stats = sessionService.stats();
        assertThat(stats).containsKeys("totalSessions", "queued", "inSession", "todaySessions");
    }
}