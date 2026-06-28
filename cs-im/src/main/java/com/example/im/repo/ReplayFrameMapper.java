package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.ReplayFrame;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplayFrameMapper extends BaseMapper<ReplayFrame> {
    /** 按 sessionId + seq 升序查所有帧 */
    List<ReplayFrame> findBySessionOrderBySeq(@Param("sessionId") Long sessionId);

    /** 按 sessionId + frameKind 查 */
    List<ReplayFrame> findBySessionAndKind(@Param("sessionId") Long sessionId,
                                           @Param("kind") String kind);

    /** 统计某 session 帧数 */
    int countBySession(@Param("sessionId") Long sessionId);

    /** 查 session 最大 seq (下次插入时 +1) */
    Integer maxSeqBySession(@Param("sessionId") Long sessionId);
}