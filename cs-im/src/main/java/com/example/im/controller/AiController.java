package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.AiKnowledge;
import com.example.im.domain.AiSuggestion;
import com.example.im.service.AiAssistantService;
import com.example.im.service.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 助手 REST API — 坐席工作台使用
 *
 * @author MiniMax
 */
@RestController
@RequestMapping("/im/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService assistantService;
    private final AiKnowledgeService knowledgeService;

    /** 即时生成推荐（同步） */
    @PostMapping("/suggest")
    public ApiResponse<AiSuggestion> generate(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        String customerId = (String) body.get("customerId");
        String agentUsername = (String) body.get("agentUsername");
        String customerMessage = (String) body.get("customerMessage");
        Long triggerMessageId = body.get("triggerMessageId") == null ? null
            : ((Number) body.get("triggerMessageId")).longValue();

        AiSuggestion s = assistantService.generateSuggestion(
            sessionId, customerId, agentUsername, triggerMessageId, customerMessage);
        return ApiResponse.ok(s);
    }

    /** 异步生成 + WebSocket 推送 */
    @PostMapping("/suggest/async")
    public ApiResponse<String> generateAsync(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        String customerId = (String) body.get("customerId");
        String agentUsername = (String) body.get("agentUsername");
        String customerMessage = (String) body.get("customerMessage");
        Long triggerMessageId = body.get("triggerMessageId") == null ? null
            : ((Number) body.get("triggerMessageId")).longValue();

        assistantService.generateSuggestionAsync(sessionId, customerId, agentUsername, triggerMessageId, customerMessage);
        return ApiResponse.ok("queued");
    }

    /** 坐席反馈 */
    @PostMapping("/feedback")
    public ApiResponse<String> feedback(@RequestBody Map<String, Object> body) {
        Long suggestionId = ((Number) body.get("suggestionId")).longValue();
        String agentUsername = (String) body.get("agentUsername");
        String feedbackType = (String) body.get("feedbackType");
        Integer rating = body.get("rating") == null ? null : ((Number) body.get("rating")).intValue();
        String modifiedContent = (String) body.get("modifiedContent");

        assistantService.recordFeedback(suggestionId, agentUsername, feedbackType, rating, modifiedContent);
        return ApiResponse.ok("recorded");
    }

    /** 坐席历史推荐 */
    @GetMapping("/recent")
    public ApiResponse<List<AiSuggestion>> recent(@RequestParam String agentUsername,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(assistantService.recentSuggestions(agentUsername, limit));
    }

    /** 知识库浏览 */
    @GetMapping("/knowledge")
    public ApiResponse<List<AiKnowledge>> knowledge(@RequestParam(defaultValue = "FAQ") String category,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(knowledgeService.listByCategory(category, limit));
    }

    /** 知识库语义检索 */
    @GetMapping("/knowledge/search")
    public ApiResponse<List<AiKnowledge>> search(@RequestParam String q,
                                                  @RequestParam(defaultValue = "5") int topK) {
        return ApiResponse.ok(knowledgeService.search(q, topK));
    }

    /** 知识库统计 */
    @GetMapping("/knowledge/stats")
    public ApiResponse<Object> stats() {
        return ApiResponse.ok(knowledgeService.stats());
    }
}