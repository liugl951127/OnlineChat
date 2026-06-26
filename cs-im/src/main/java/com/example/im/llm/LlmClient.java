package com.example.im.llm;

import java.util.List;

/**
 * LLM 客户端统一接口 — 屏蔽不同 LLM 厂商差异
 *
 * <p>实现类：
 * <ul>
 *   <li>{@link MockLlmClient} — 离线 mock（关键词模板匹配）</li>
 *   <li>{@link OpenAiLlmClient} — OpenAI GPT-3.5/4（生产）</li>
 *   <li>{@link QwenLlmClient} — 通义千问（国内生产）</li>
 *   <li>{@link OllamaLlmClient} — 本地 Ollama（开发测试）</li>
 * </ul>
 *
 * <p>使用 {@link org.springframework.beans.factory.annotation.Qualifier} 切换实现。
 */
public interface LlmClient {

    /**
     * 简单对话 — 单轮
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 多轮对话 — 上下文
     *
     * @param messages [{"role": "user", "content": "..."}, ...]
     */
    String chat(List<ChatMessage> messages);

    record ChatMessage(String role, String content) {}
}