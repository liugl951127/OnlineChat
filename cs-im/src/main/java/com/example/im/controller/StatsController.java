package com.example.im.controller;

import com.example.common.ApiException;
import com.example.common.ApiResponse;
import com.example.common.SecurityContext;
import com.example.common.SecurityContextHolder;
import com.example.im.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 统计 API (v2.2.83)
 *
 * <p>仅 ADMIN / SUPERVISOR 可访问.
 *
 * <p>路径前缀: {@code /im/stats/dashboard/*}
 */
@RestController
@RequestMapping("/im/stats/dashboard")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 检查权限 (ADMIN 或 SUPERVISOR) */
    private void requireAdmin() {
        SecurityContext ctx = SecurityContextHolder.current();
        if (ctx == null) throw new ApiException(401, "未登录");
        String role = ctx.getRole();
        if (!"ADMIN".equals(role) && !"SUPERVISOR".equals(role)
                && !"AGENT".equals(role)) {  // 坐席也能看自己相关统计
            throw new ApiException(403, "无权限");
        }
    }

    /** 一次性拿全部数据 (前端主入口) */
    @GetMapping("/all")
    public ApiResponse<Map<String, Object>> all() {
        requireAdmin();
        return ApiResponse.ok(statsService.dashboard());
    }

    // ============ 单项查询 (增量刷新用) ============

    @GetMapping("/session/totals")
    public ApiResponse<Map<String, Object>> sessionTotals() {
        requireAdmin();
        return ApiResponse.ok(statsService.getSessionTotals());
    }

    @GetMapping("/session/by-status")
    public ApiResponse<List<Map<String, Object>>> sessionByStatus() {
        requireAdmin();
        return ApiResponse.ok(statsService.getSessionByStatus());
    }

    @GetMapping("/session/by-agent")
    public ApiResponse<List<Map<String, Object>>> sessionByAgent() {
        requireAdmin();
        return ApiResponse.ok(statsService.getSessionByAgent());
    }

    @GetMapping("/session/trend")
    public ApiResponse<List<Map<String, Object>>> sessionTrend(@RequestParam(defaultValue = "7") int days) {
        requireAdmin();
        return ApiResponse.ok(statsService.getSessionTrend(days));
    }

    @GetMapping("/session/hour-today")
    public ApiResponse<List<Map<String, Object>>> sessionByHourToday() {
        requireAdmin();
        return ApiResponse.ok(statsService.getSessionByHourToday());
    }

    @GetMapping("/message/totals")
    public ApiResponse<Map<String, Object>> messageTotals() {
        requireAdmin();
        return ApiResponse.ok(statsService.getMessageTotals());
    }

    @GetMapping("/message/by-role")
    public ApiResponse<List<Map<String, Object>>> messageByRole() {
        requireAdmin();
        return ApiResponse.ok(statsService.getMessageByRole());
    }

    @GetMapping("/message/by-type")
    public ApiResponse<List<Map<String, Object>>> messageByType() {
        requireAdmin();
        return ApiResponse.ok(statsService.getMessageByType());
    }

    @GetMapping("/user/totals")
    public ApiResponse<Map<String, Object>> userTotals() {
        requireAdmin();
        return ApiResponse.ok(statsService.getUserTotals());
    }

    @GetMapping("/user/by-subscribe")
    public ApiResponse<List<Map<String, Object>>> userBySubscribe() {
        requireAdmin();
        return ApiResponse.ok(statsService.getUserBySubscribe());
    }

    @GetMapping("/user/trend")
    public ApiResponse<List<Map<String, Object>>> userTrend(@RequestParam(defaultValue = "7") int days) {
        requireAdmin();
        return ApiResponse.ok(statsService.getUserTrend(days));
    }

    @GetMapping("/replay/totals")
    public ApiResponse<Map<String, Object>> replayTotals() {
        requireAdmin();
        return ApiResponse.ok(statsService.getReplayTotals());
    }

    @GetMapping("/replay/storage")
    public ApiResponse<Map<String, Object>> replayStorage() {
        requireAdmin();
        return ApiResponse.ok(statsService.getReplayStorage());
    }

    @GetMapping("/replay/trend")
    public ApiResponse<List<Map<String, Object>>> replayTrend(@RequestParam(defaultValue = "7") int days) {
        requireAdmin();
        return ApiResponse.ok(statsService.getReplayTrend(days));
    }

    @GetMapping("/ticket/by-status")
    public ApiResponse<List<Map<String, Object>>> ticketByStatus() {
        requireAdmin();
        return ApiResponse.ok(statsService.getTicketByStatus());
    }

    @GetMapping("/kyc")
    public ApiResponse<Map<String, Object>> kycStats() {
        requireAdmin();
        return ApiResponse.ok(statsService.getKycStats());
    }

    @GetMapping("/order/by-status")
    public ApiResponse<List<Map<String, Object>>> orderByStatus() {
        requireAdmin();
        return ApiResponse.ok(statsService.getOrderByStatus());
    }

    @GetMapping("/revenue/today")
    public ApiResponse<Map<String, Object>> revenueToday() {
        requireAdmin();
        return ApiResponse.ok(statsService.getRevenueToday());
    }
}