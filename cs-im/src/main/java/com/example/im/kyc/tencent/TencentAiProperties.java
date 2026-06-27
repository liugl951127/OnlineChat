package com.example.im.kyc.tencent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 AI 配置（v2.2.0）
 *
 * <p>申请地址：https://console.cloud.tencent.com/cam/capi
 *
 * <p>配置：
 * <pre>
 * tencent:
 *   ai:
 *     secret-id: AKIDxxx
 *     secret-key: xxx
 *     region: ap-guangzhou
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tencent.ai")
public class TencentAiProperties {
    /** 腾讯云 API 密钥 ID */
    private String secretId;
    /** 腾讯云 API 密钥 Key */
    private String secretKey;
    /** 地域（默认广州） */
    private String region = "ap-guangzhou";
    /** IAI 接口域名 */
    private String endpoint = "iai.tencentcloudapi.com";

    /** 人脸比对阈值（0-100，≥ 此值视为同一人） */
    private double faceMatchThreshold = 80.0;

    /** 活体检测超时（毫秒） */
    private int livenessTimeoutMs = 10000;
}