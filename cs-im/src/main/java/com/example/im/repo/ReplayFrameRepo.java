package com.example.im.repo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.im.domain.ReplayFrame;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReplayFrameRepo {

    private final ReplayFrameMapper mapper;

    public ReplayFrame save(ReplayFrame f) {
        if (f.getId() == null) {
            mapper.insert(f);
        } else {
            mapper.updateById(f);
        }
        return f;
    }

    public List<ReplayFrame> findBySessionOrderBySeq(Long sessionId) {
        return mapper.findBySessionOrderBySeq(sessionId);
    }

    public List<ReplayFrame> findBySessionAndKind(Long sessionId, String kind) {
        return mapper.findBySessionAndKind(sessionId, kind);
    }

    public int countBySession(Long sessionId) {
        return mapper.countBySession(sessionId);
    }

    public int nextSeq(Long sessionId) {
        Integer max = mapper.maxSeqBySession(sessionId);
        return (max == null ? -1 : max) + 1;
    }

    public ReplayFrame findById(Long id) {
        return mapper.selectById(id);
    }

    /** 根据 messageId 查帧 (用于消息回放跳转) */
    public List<ReplayFrame> findByMessageId(Long messageId) {
        return mapper.selectList(new QueryWrapper<ReplayFrame>().eq("message_id", messageId));
    }
}