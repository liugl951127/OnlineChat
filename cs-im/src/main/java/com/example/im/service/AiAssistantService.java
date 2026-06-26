package com.example.im.service;

import com.example.im.domain.AiFeedback;
import com.example.im.domain.AiKnowledge;
import com.example.im.domain.AiSuggestion;
import com.example.im.llm.LlmClient;
import com.example.im.llm.LlmConfig;
import com.example.im.repo.AiFeedbackMapper;
import com.example.im.repo.AiSuggestionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 坐席 AI 助手服务 — 实时推荐话术
 *
 * <p>核心方法 {@link #generateSuggestion}：
 * <ol>
 *   <li>接收客户消息</li>
 *   <li>RAG 检索相关知识库</li>
 *   <li>调用 LLM 生成推荐话术</li>
 *   <li>写入 ai_suggestion</li>
 *   <li>通过 WebSocket 推送给坐席 /user/queue/ai-suggestion</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final LlmClient llmClient;
    private final LlmConfig llmConfig;
    private final AiKnowledgeService knowledgeService;
    private final AiSuggestionMapper suggestionMapper;
    private final AiFeedbackMapper feedbackMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是金融客服系统的坐席 AI 助手。
        你的任务是：当客户发送问题时，根据客户问题 + 知识库内容，生成一段推荐回复话术（150 字以内），
        让坐席参考。话术要：礼貌、专业、引导客户、提供具体步骤或数字。
        如果客户问题不清楚，可以反问。
        """;

    /**
     * 异步生成推荐话术 — 不阻塞主消息接收
     */
    @Async
    public void generateSuggestionAsync(Long sessionId, String customerId, String agentUsername,
                                        Long triggerMessageId, String customerMessage) {
        try {
            AiSuggestion suggestion = generateSuggestion(sessionId, customerId, agentUsername,
                triggerMessageId, customerMessage);
            // WebSocket 推送给坐席
            pushToAgent(agentUsername, suggestion);
        } catch (Exception e) {
            log.error("[AI] 生成推荐话术失败 customerMessage={}", customerMessage, e);
        }
    }

    /**
     * 同步生成推荐（用于测试 / 即时调用）
     */
    public AiSuggestion generateSuggestion(Long sessionId, String customerId, String agentUsername,
                                            Long triggerMessageId, String customerMessage) {
        // 1. RAG 检索
        String context = knowledgeService.buildContextPrompt(customerMessage, llmConfig.getRagTopK());

        // 2. 拼 prompt
        String userPrompt = buildUserPrompt(customerMessage, context);

        // 3. LLM 生成
        String reply = llmClient.chat(SYSTEM_PROMPT, userPrompt);

        // 4. 计算置信度（基于上下文匹配）
        double confidence = computeConfidence(customerMessage, context);

        // 5. 落库
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setSessionId(sessionId);
        suggestion.setCustomerId(customerId);
        suggestion.setAgentUsername(agentUsername);
        suggestion.setTriggerMessageId(triggerMessageId);
        suggestion.setSuggestionType(detectType(customerMessage));
        suggestion.setContent(truncate(reply, llmConfig.getMaxSuggestionLength()));
        suggestion.setConfidence(confidence);
        suggestion.setSources(extractSourceIds(context));
        suggestion.setUsed(false);
        suggestionMapper.insert(suggestion);

        log.info("[AI] 推荐生成 sessionId={}, type={}, confidence={}, content={}",
            sessionId, suggestion.getSuggestionType(), confidence, suggestion.getContent());
        return suggestion;
    }

    /**
     * 坐席反馈（采纳/跳过/评分）
     */
    public void recordFeedback(Long suggestionId, String agentUsername,
                                String feedbackType, Integer rating, String modifiedContent) {
        AiFeedback fb = new AiFeedback();
        fb.setSuggestionId(suggestionId);
        fb.setAgentUsername(agentUsername);
        fb.setFeedbackType(feedbackType);
        fb.setRating(rating);
        fb.setModifiedContent(modifiedContent);
        feedbackMapper.insert(fb);

        // 更新 suggestion 状态
        AiSuggestion s = suggestionMapper.selectById(suggestionId);
        if (s != null) {
            if ("USED".equals(feedbackType)) s.setUsed(true);
            if (rating != null) s.setRating(rating);
            suggestionMapper.updateById(s);
        }
    }

    /**
     * 历史推荐（坐席端展示）
     */
    public List<AiSuggestion> recentSuggestions(String agentUsername, int limit) {
        return suggestionMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiSuggestion>()
                .eq("agent_username", agentUsername)
                .orderByDesc("created_at")
                .last("LIMIT " + limit)
        );
    }

    // ----------------- private helpers -----------------

    private String buildUserPrompt(String userMessage, String context) {
        if (context.isEmpty()) {
            return "客户说：" + userMessage + "\n\n请生成推荐话术：";
        }
        return context + "\n\n客户说：" + userMessage + "\n\n请基于以上知识生成推荐话术：";
    }

    private double computeConfidence(String query, String context) {
        if (context.isEmpty()) return 50.0;
        // 简单启发式：上下文越长 → 置信度越高（mock）
        double base = 70.0;
        double bonus = Math.min(20.0, context.length() / 30.0);
        return Math.min(95.0, base + bonus);
    }

    private String detectType(String message) {
        String m = message.toLowerCase();
        if (m.contains("投诉") || m.contains("退款")) return "ACTION";
        if (m.contains("理财") || m.contains("基金") || m.contains("保险")) return "KNOWLEDGE";
        if (m.contains("怎么") || m.contains("如何")) return "FAQ";
        return "REPLY";
    }

    private String extractSourceIds(String context) {
        // 简化：返回 "knowledge_top3"
        return context.isEmpty() ? "" : "knowledge_top3";
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "...");
    }

    private void pushToAgent(String agentUsername, AiSuggestion suggestion) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "AI_SUGGESTION");
            payload.put("suggestionId", suggestion.getId());
            payload.put("sessionId", suggestion.getSessionId());
            payload.put("suggestionType", suggestion.getSuggestionType());
            payload.put("content", suggestion.getContent());
            payload.put("confidence", suggestion.getConfidence());
            payload.put("createdAt", suggestion.getCreatedAt());

            String destination = "/user/queue/ai-suggestion";
            messagingTemplate.convertAndSendToUser(agentUsername, destination, payload);
            log.debug("[AI] WebSocket 推送 {} -> {}", agentUsername, destination);
        } catch (Exception e) {
            log.warn("[AI] WebSocket 推送失败（坐席未连接？）agent={}", agentUsername, e.getMessage());
        }
    }
}