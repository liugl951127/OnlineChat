package com.example.im.kyc.baidu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百度 OCR 配置（v2.1.1）
 *
 * <p>从 application.yml 注入：
 * <pre>
 * baidu:
 *   ocr:
 *     app-id: 你的 AppID
 *     api-key: 你的 API Key
 *     secret-key: 你的 Secret Key
 * </pre>
 *
 * <p>申请地址：https://console.bce.baidu.com/ai/#/ai/ocr/overview/index
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "baidu.ocr")
public class BaiduOcrProperties {
    /** 百度智能云应用的 AppID */
    private String appId;
    /** API Key */
    private String apiKey;
    /** Secret Key */
    private String secretKey;
    /** OCR 接口地址（一般不用改） */
    private String endpoint = "https://aip.baidubce.com/rest/2.0/ocr/v1";
    /** HTTP 超时（毫秒） */
    private int timeoutMs = 5000;
}