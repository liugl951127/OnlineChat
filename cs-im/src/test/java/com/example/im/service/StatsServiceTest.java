package com.example.im.service;

import com.example.im.repo.StatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatsService 单元测试 (v2.2.83)
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private StatsMapper statsMapper;
    @InjectMocks private StatsService statsService;

    @BeforeEach
    void setup() {
        // 默认 stub: 大部分查询返空 Map/List
        lenient().when(statsMapper.sessionTotals()).thenReturn(Map.of("total", 0, "today", 0, "active", 0));
        lenient().when(statsMapper.sessionByStatus()).thenReturn(List.of());
        lenient().when(statsMapper.sessionByAgent()).thenReturn(List.of());
        lenient().when(statsMapper.sessionTrend(anyInt())).thenReturn(List.of());
        lenient().when(statsMapper.sessionByHourToday()).thenReturn(List.of());
        lenient().when(statsMapper.messageTotals()).thenReturn(Map.of("total", 0, "today", 0, "last_hour", 0));
        lenient().when(statsMapper.messageByRole()).thenReturn(List.of());
        lenient().when(statsMapper.messageByType()).thenReturn(List.of());
        lenient().when(statsMapper.userTotals()).thenReturn(Map.of("total", 0, "today", 0, "subscribed", 0));
        lenient().when(statsMapper.userBySubscribe()).thenReturn(List.of());
        lenient().when(statsMapper.userTrend(anyInt())).thenReturn(List.of());
        lenient().when(statsMapper.replayTotals()).thenReturn(Map.of("total", 0, "success", 0, "failed", 0, "today_success", 0));
        lenient().when(statsMapper.replayStorage()).thenReturn(Map.of("total_frames", 0, "total_duration_ms", 0));
        lenient().when(statsMapper.replayTrend(anyInt())).thenReturn(List.of());
        lenient().when(statsMapper.ticketByStatus()).thenReturn(List.of());
        lenient().when(statsMapper.kycStats()).thenReturn(Map.of("total", 0, "approved", 0, "rejected", 0, "today", 0));
        lenient().when(statsMapper.orderByStatus()).thenReturn(List.of());
        lenient().when(statsMapper.revenueToday()).thenReturn(Map.of("today_settled", 0.0, "total_settled", 0.0));
    }

    @Test
    void testDashboard_returns18Aggregations() {
        Map<String, Object> result = statsService.dashboard();
        assertNotNull(result);
        // 18 个聚合项
        assertEquals(18, result.size());
        // 验证 key
        assertTrue(result.containsKey("sessionTotals"));
        assertTrue(result.containsKey("sessionByStatus"));
        assertTrue(result.containsKey("sessionByAgent"));
        assertTrue(result.containsKey("sessionTrend"));
        assertTrue(result.containsKey("sessionByHourToday"));
        assertTrue(result.containsKey("messageTotals"));
        assertTrue(result.containsKey("messageByRole"));
        assertTrue(result.containsKey("messageByType"));
        assertTrue(result.containsKey("userTotals"));
        assertTrue(result.containsKey("userBySubscribe"));
        assertTrue(result.containsKey("userTrend"));
        assertTrue(result.containsKey("replayTotals"));
        assertTrue(result.containsKey("replayStorage"));
        assertTrue(result.containsKey("replayTrend"));
        assertTrue(result.containsKey("ticketByStatus"));
        assertTrue(result.containsKey("kycStats"));
        assertTrue(result.containsKey("orderByStatus"));
        assertTrue(result.containsKey("revenueToday"));
    }

    @Test
    void testDashboard_withRealData() {
        // 模拟真实数据
        Map<String, Object> sess = new HashMap<>();
        sess.put("total", 100);
        sess.put("today", 10);
        sess.put("active", 5);
        when(statsMapper.sessionTotals()).thenReturn(sess);

        when(statsMapper.sessionByStatus()).thenReturn(Arrays.asList(
                Map.of("k", "IN_SESSION", "v", 5),
                Map.of("k", "ENDED", "v", 95)
        ));

        Map<String, Object> result = statsService.dashboard();
        Map<String, Object> sessTotal = (Map<String, Object>) result.get("sessionTotals");
        assertEquals(100, sessTotal.get("total"));
        assertEquals(10, sessTotal.get("today"));

        List<Map<String, Object>> byStatus = (List<Map<String, Object>>) result.get("sessionByStatus");
        assertEquals(2, byStatus.size());
        assertEquals("IN_SESSION", byStatus.get(0).get("k"));
    }

    @Test
    void testDashboard_callsTrendWith7Days() {
        statsService.dashboard();
        verify(statsMapper).sessionTrend(7);
        verify(statsMapper).userTrend(7);
        verify(statsMapper).replayTrend(7);
    }

    @Test
    void testSingleItemMethods_callMapper() {
        statsService.getSessionTotals();
        statsService.getSessionByStatus();
        statsService.getSessionByAgent();
        statsService.getSessionTrend(30);
        statsService.getSessionByHourToday();
        statsService.getMessageTotals();
        statsService.getMessageByRole();
        statsService.getMessageByType();
        statsService.getUserTotals();
        statsService.getUserBySubscribe();
        statsService.getUserTrend(30);
        statsService.getReplayTotals();
        statsService.getReplayStorage();
        statsService.getReplayTrend(30);
        statsService.getTicketByStatus();
        statsService.getKycStats();
        statsService.getOrderByStatus();
        statsService.getRevenueToday();

        verify(statsMapper).sessionTotals();
        verify(statsMapper).sessionByStatus();
        verify(statsMapper).sessionByAgent();
        verify(statsMapper).sessionTrend(30);
        verify(statsMapper).sessionByHourToday();
        verify(statsMapper).messageTotals();
        verify(statsMapper).messageByRole();
        verify(statsMapper).messageByType();
        verify(statsMapper).userTotals();
        verify(statsMapper).userBySubscribe();
        verify(statsMapper).userTrend(30);
        verify(statsMapper).replayTotals();
        verify(statsMapper).replayStorage();
        verify(statsMapper).replayTrend(30);
        verify(statsMapper).ticketByStatus();
        verify(statsMapper).kycStats();
        verify(statsMapper).orderByStatus();
        verify(statsMapper).revenueToday();
    }

    @Test
    void testDashboard_handlesNulls() {
        when(statsMapper.sessionTotals()).thenReturn(null);
        Map<String, Object> result = statsService.dashboard();
        assertNotNull(result);
        // null 不影响返回, 仍然返回 Map 容器
        assertEquals(18, result.size());
    }
}