package com.example.auth.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 全局超时配置（v2.2.35）。
 *
 * <p>避免默认无超时导致外部接口（微信 / GitHub / Google）慢响应时线程卡死。
 *
 * <ul>
 *   <li>connectTimeout: 5s — TCP 连接超时</li>
 *   <li>readTimeout:    10s — 读响应超时</li>
 * </ul>
 */
@Configuration
public class RestTemplateConfig {

    /** 主 RestTemplate bean (所有 service 注入这个) */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return new RestTemplate(factory);
    }
}
