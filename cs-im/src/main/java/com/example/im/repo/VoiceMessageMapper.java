package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.VoiceMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VoiceMessageMapper extends BaseMapper<VoiceMessage> {

    @Select("SELECT * FROM voice_message WHERE deleted = 0 AND session_id = #{sessionId} " +
            "ORDER BY created_at ASC")
    List<VoiceMessage> findBySessionId(Long sessionId);
}