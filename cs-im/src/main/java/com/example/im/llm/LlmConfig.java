package com.example.im.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 配置 — 通过 application.yml 注入
 *
 * <p>示例：
 * <pre>
 * llm:
 *   provider: mock          # mock / openai / qwen / ollama
 *   model: qwen-turbo
 *   api-key: sk-xxx
 *   endpoint: https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
 *   timeout-ms: 5000
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    /** mock / openai / qwen / ollama */
    private String provider = "mock";
    private String model = "mock-v1";
    private String apiKey = "";
    private String endpoint = "";
    private int timeoutMs = 5000;
    /** RAG 检索 topK */
    private int ragTopK = 3;
    /** 推荐话术最大长度 */
    private int maxSuggestionLength = 500;
}