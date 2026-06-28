package com.example.im.repo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统计聚合查询 (v2.2.83)
 *
 * <p>用于 Dashboard / 运营大屏, 直接用 SQL 聚合避免 N+1.
 */
@Mapper
public interface StatsMapper {

    // ============ 会话统计 ============

    /** 总会话数 (今日/昨日/总) */
    Map<String, Object> sessionTotals();

    /** 会话状态分布 (按 status 分组) */
    List<Map<String, Object>> sessionByStatus();

    /** 按坐席分组统计会话数 + 消息数 */
    List<Map<String, Object>> sessionByAgent();

    /** 按天聚合最近 N 天会话数 (用于折线图) */
    List<Map<String, Object>> sessionTrend(@Param("days") int days);

    /** 按小时聚合今日会话数 (用于小时分布) */
    List<Map<String, Object>> sessionByHourToday();

    // ============ 消息统计 ============

    /** 消息总数 + 今日新增 */
    Map<String, Object> messageTotals();

    /** 消息按角色分组 (CUSTOMER/AGENT/ROBOT/SYSTEM) */
    List<Map<String, Object>> messageByRole();

    /** 消息类型分布 (TEXT/IMAGE/FILE/etc) */
    List<Map<String, Object>> messageByType();

    // ============ 用户统计 ============

    /** 用户总数 + 今日新增 + 按渠道 */
    Map<String, Object> userTotals();

    /** 关注公众号 vs 未关注用户数 */
    List<Map<String, Object>> userBySubscribe();

    /** 最近 7 天新增用户 */
    List<Map<String, Object>> userTrend(@Param("days") int days);

    // ============ 视频回溯统计 ============

    /** 视频合成统计 (今日合成数 / 总数 / 成功率) */
    Map<String, Object> replayTotals();

    /** 回溯视频总大小 / 总时长 */
    Map<String, Object> replayStorage();

    /** 最近 N 天合成数 */
    List<Map<String, Object>> replayTrend(@Param("days") int days);

    // ============ 工单/KYC/订单统计 ============

    /** 工单状态分布 */
    List<Map<String, Object>> ticketByStatus();

    /** KYC 通过率 */
    Map<String, Object> kycStats();

    /** 订单按状态分组 */
    List<Map<String, Object>> orderByStatus();

    /** 今日营收 */
    Map<String, Object> revenueToday();
}