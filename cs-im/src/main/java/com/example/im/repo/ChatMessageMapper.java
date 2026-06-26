package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis Plus 通用 CRUD
import com.example.im.domain.ChatMessage;            // 消息实体
import org.apache.ibatis.annotations.Mapper;          // MyBatis Mapper 注解（自动扫描）

import java.util.List;                                // 列表返回类型

/**
 * 消息 Mapper（MyBatis Plus BaseMapper + 自定义方法）
 *
 * <p>自定义方法在 {@code classpath:mapper/ChatMessageMapper.xml} 中实现。
 */
@Mapper                                              // 由 MyBatis 扫描为代理
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按会话 ID 查全部消息（升序）
     */
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    /**
     * 实时拉取：查 ID > lastId 的消息（升序）
     */
    List<ChatMessage> findBySessionIdAfter(Long sessionId, Long lastId);

    /**
     * 时间范围查询（视频回溯）
     *
     * @param sessionId 会话 ID
     * @param startTime 起始时间（ISO，可为 null 表示不限制）
     * @param endTime   结束时间（ISO，可为 null 表示不限制）
     */
    List<ChatMessage> findBySessionIdAndTimeRange(Long sessionId, String startTime, String endTime);
}