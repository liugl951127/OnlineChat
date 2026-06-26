package com.example.im.service;

import com.example.im.domain.AiKnowledge;
import com.example.im.llm.LlmClient;
import com.example.im.repo.AiKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 知识库服务 — RAG 检索增强
 *
 * <p>三种检索方式：
 * <ol>
 *   <li>关键词 TF 匹配（mock 模式）</li>
 *   <li>向量余弦相似度（生产模式，对接 embedding 服务）</li>
 *   <li>全文检索（MySQL FULLTEXT，生产可换 ES）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final AiKnowledgeMapper knowledgeMapper;
    private final LlmClient llmClient;

    /**
     * 检索相关知识 — 当前 mock 实现用关键词 TF + 标签匹配
     */
    public List<AiKnowledge> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        // 加载所有 ACTIVE 知识（生产可用向量索引 + 全文检索）
        List<AiKnowledge> all = knowledgeMapper.findTopByUsage(50);

        // 计算每条的相关性分数
        List<Map.Entry<AiKnowledge, Double>> scored = new ArrayList<>();
        for (AiKnowledge k : all) {
            double score = relevanceScore(query, k);
            if (score > 0) {
                scored.add(Map.entry(k, score));
            }
        }

        return scored.stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 简单相关性评分：标题/内容/标签的关键词命中数
     */
    private double relevanceScore(String query, AiKnowledge k) {
        String q = query.toLowerCase();
        String[] tokens = q.split("\\s+|[,，、.。!！?？]");
        double score = 0;
        for (String t : tokens) {
            if (t.isBlank()) continue;
            if (k.getTitle() != null && k.getTitle().toLowerCase().contains(t)) score += 2.0;
            if (k.getContent() != null && k.getContent().toLowerCase().contains(t)) score += 1.0;
            if (k.getTags() != null && k.getTags().toLowerCase().contains(t)) score += 1.5;
        }
        return score;
    }

    /**
     * 把检索到的知识拼成 LLM 上下文
     */
    public String buildContextPrompt(String query, int topK) {
        List<AiKnowledge> hits = search(query, topK);
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("以下是与客户问题相关的知识库内容：\n\n");
        for (AiKnowledge k : hits) {
            sb.append("【").append(k.getCategory()).append("】")
              .append(k.getTitle()).append("\n")
              .append(k.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 列出某类全部知识（用于 Agent 工作台浏览）
     */
    public List<AiKnowledge> listByCategory(String category, int limit) {
        return knowledgeMapper.findByCategory(category, limit);
    }

    /**
     * 统计
     */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", knowledgeMapper.selectCount(null));
        return m;
    }
}