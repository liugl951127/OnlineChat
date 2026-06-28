package com.example.im.service;

import com.example.im.repo.StatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务 (v2.2.83)
 *
 * <p>Dashboard / 运营大屏聚合统计.
 *
 * <p>特性:
 * <ul>
 *   <li>全部用 SQL 聚合查询, 单次请求 < 50ms</li>
 *   <li>跨库统计 (cs_im + cs_auth + cs_message)</li>
 *   <li>前端用 1 个 dashboard 端点拿全部数据</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;

    /**
     * 获取 Dashboard 全量数据 (1 次请求拿全部)
     *
     * @return 包含 14 个聚合指标的 Map
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new HashMap<>();

        // 会话
        result.put("sessionTotals", statsMapper.sessionTotals());
        result.put("sessionByStatus", statsMapper.sessionByStatus());
        result.put("sessionByAgent", statsMapper.sessionByAgent());
        result.put("sessionTrend", statsMapper.sessionTrend(7));
        result.put("sessionByHourToday", statsMapper.sessionByHourToday());

        // 消息
        result.put("messageTotals", statsMapper.messageTotals());
        result.put("messageByRole", statsMapper.messageByRole());
        result.put("messageByType", statsMapper.messageByType());

        // 用户 (跨库)
        result.put("userTotals", statsMapper.userTotals());
        result.put("userBySubscribe", statsMapper.userBySubscribe());
        result.put("userTrend", statsMapper.userTrend(7));

        // 视频回溯
        result.put("replayTotals", statsMapper.replayTotals());
        result.put("replayStorage", statsMapper.replayStorage());
        result.put("replayTrend", statsMapper.replayTrend(7));

        // 工单/KYC/订单
        result.put("ticketByStatus", statsMapper.ticketByStatus());
        result.put("kycStats", statsMapper.kycStats());
        result.put("orderByStatus", statsMapper.orderByStatus());
        result.put("revenueToday", statsMapper.revenueToday());

        return result;
    }

    // ============ 单项查询 (供前端增量刷新) ============

    public Map<String, Object> getSessionTotals() { return statsMapper.sessionTotals(); }
    public List<Map<String, Object>> getSessionByStatus() { return statsMapper.sessionByStatus(); }
    public List<Map<String, Object>> getSessionByAgent() { return statsMapper.sessionByAgent(); }
    public List<Map<String, Object>> getSessionTrend(int days) { return statsMapper.sessionTrend(days); }
    public List<Map<String, Object>> getSessionByHourToday() { return statsMapper.sessionByHourToday(); }
    public Map<String, Object> getMessageTotals() { return statsMapper.messageTotals(); }
    public List<Map<String, Object>> getMessageByRole() { return statsMapper.messageByRole(); }
    public List<Map<String, Object>> getMessageByType() { return statsMapper.messageByType(); }
    public Map<String, Object> getUserTotals() { return statsMapper.userTotals(); }
    public List<Map<String, Object>> getUserBySubscribe() { return statsMapper.userBySubscribe(); }
    public List<Map<String, Object>> getUserTrend(int days) { return statsMapper.userTrend(days); }
    public Map<String, Object> getReplayTotals() { return statsMapper.replayTotals(); }
    public Map<String, Object> getReplayStorage() { return statsMapper.replayStorage(); }
    public List<Map<String, Object>> getReplayTrend(int days) { return statsMapper.replayTrend(days); }
    public List<Map<String, Object>> getTicketByStatus() { return statsMapper.ticketByStatus(); }
    public Map<String, Object> getKycStats() { return statsMapper.kycStats(); }
    public List<Map<String, Object>> getOrderByStatus() { return statsMapper.orderByStatus(); }
    public Map<String, Object> getRevenueToday() { return statsMapper.revenueToday(); }
}