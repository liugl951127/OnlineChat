package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiKnowledgeMapper extends BaseMapper<AiKnowledge> {

    @Select("SELECT * FROM ai_knowledge WHERE deleted = 0 AND status = 'ACTIVE' " +
            "ORDER BY view_count DESC, helpful_count DESC LIMIT #{limit}")
    List<AiKnowledge> findTopByUsage(int limit);

    @Select("SELECT * FROM ai_knowledge WHERE deleted = 0 AND status = 'ACTIVE' " +
            "AND category = #{category} ORDER BY id DESC LIMIT #{limit}")
    List<AiKnowledge> findByCategory(String category, int limit);
}