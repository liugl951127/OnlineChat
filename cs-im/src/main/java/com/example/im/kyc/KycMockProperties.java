package com.example.im.kyc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * KYC Mock 模式开关（v2.1.0+）
 *
 * <p>生产推荐设为 false，对接百度/阿里云/腾讯云真实 API。
 *
 * <p>示例：
 * <pre>
 * kyc:
 *   mock:
 *     ocr: false
 *     liveness: false
 *     face-match: false
 *     video-record: false
 *     bank-card: false
 *     risk-control: false
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kyc.mock")
public class KycMockProperties {
    /** 身份证 OCR 识别 */
    private boolean ocr = false;
    /** 活体检测 */
    private boolean liveness = false;
    /** 人脸比对 */
    private boolean faceMatch = false;
    /** 视频双录存储 */
    private boolean videoRecord = false;
    /** 银行卡四要素鉴权 */
    private boolean bankCard = false;
    /** 风控评估 */
    private boolean riskControl = false;
}