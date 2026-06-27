package com.example.im.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 全局超时配置（v2.2.35）。
 *
 * <p>KYC / OCR / 人脸识别等外部 API 必须有超时：
 * <ul>
 *   <li>connectTimeout: 5s</li>
 *   <li>readTimeout:    30s（KYC 视频上传需要更长时间）</li>
 * </ul>
 */
@Configuration
public class RestTemplateConfig {

    /** 主 RestTemplate bean (KYC / OCR / 人脸识别 注入这个) */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new RestTemplate(factory);
    }
}