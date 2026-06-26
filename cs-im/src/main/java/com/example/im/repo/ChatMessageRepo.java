package com.example.im.repo;

import com.example.im.domain.ChatMessage;            // 消息实体
import org.springframework.beans.factory.annotation.Autowired; // Spring 依赖注入
import org.springframework.stereotype.Repository;   // 标记为仓储组件

import java.util.List;                              // 列表返回类型
import java.util.Optional;                          // Optional 防 NPE

/**
 * 消息仓储（语义化包装，业务层调用此而非直接用 BaseMapper）
 *
 * <p>v1.9.0 新增：
 * <ul>
 *   <li>findBySessionIdAfter：实时拉取（poll）查询 lastId 之后的新消息</li>
 *   <li>findBySessionIdAndTimeRange：视频回溯查询时间范围内消息</li>
 * </ul>
 */
@Repository
public class ChatMessageRepo {

    /** MyBatis Plus BaseMapper 实例 */
    @Autowired private ChatMessageMapper mapper;

    /**
     * 保存消息（插入新记录）
     *
     * @param m 消息实体
     * @return 入库后的对象（含自增 ID）
     */
    public ChatMessage save(ChatMessage m) {
        mapper.insert(m);   // 插入
        return m;           // 返回引用
    }

    /**
     * 直接插入并返回受影响行数
     */
    public int insert(ChatMessage m) { return mapper.insert(m); }

    /**
     * 按 ID 查询单条
     *
     * @param id 主键
     * @return Optional<ChatMessage>（不存在返回 Optional.empty()）
     */
    public Optional<ChatMessage> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /**
     * 按会话 ID 查询所有消息（按 ID 升序）
     */
    public List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId) {
        return mapper.findBySessionIdOrderByIdAsc(sessionId);
    }

    /**
     * 更新消息（按 ID）
     */
    public int updateById(ChatMessage m) {
        return mapper.updateById(m);
    }

    /**
     * 查询会话中 ID > lastId 的消息（实时拉取用）
     *
     * @param sessionId 会话 ID
     * @param lastId    客户端最后一条消息 ID（首次传 0）
     * @return 新消息列表（按 ID 升序）
     */
    public List<ChatMessage> findBySessionIdAfter(Long sessionId, Long lastId) {
        return mapper.findBySessionIdAfter(sessionId, lastId);
    }

    /**
     * 查询会话时间范围内的消息（视频回溯用）
     *
     * @param sessionId 会话 ID
     * @param startTime 起始时间（ISO 字符串，可为 null）
     * @param endTime   结束时间（ISO 字符串，可为 null）
     * @return 消息列表（按 createdAt 升序）
     */
    public List<ChatMessage> findBySessionIdAndTimeRange(Long sessionId, String startTime, String endTime) {
        // 委托给 XML Mapper（动态 SQL）
        return mapper.findBySessionIdAndTimeRange(sessionId, startTime, endTime);
    }
}