package com.example.im.kyc.local;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 OCR + 人脸识别开关（v2.2.1）
 *
 * <p>完全离线自研实现，零外部 API 依赖。
 *
 * <p>配置：
 * <pre>
 * local:
 *   ocr:
 *     enabled: true
 *     tess-data-path: classpath:tessdata/
 *   face:
 *     enabled: true
 *     cascade-path: classpath:haarcascades/haarcascade_frontalface.xml
 *     match-threshold: 80
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "local")
public class LocalRecognitionProperties {
    private Ocr ocr = new Ocr();
    private Face face = new Face();

    @Data
    public static class Ocr {
        /** 启用本地 OCR */
        private boolean enabled = true;
        /** Tesseract 训练数据路径（含 chi_sim.traineddata 等） */
        private String tessDataPath = "classpath:tessdata/";
    }

    @Data
    public static class Face {
        /** 启用本地人脸识别 */
        private boolean enabled = true;
        /** OpenCV Haar 级联分类器路径 */
        private String cascadePath = "classpath:haarcascades/haarcascade_frontalface.xml";
        /** 人脸比对阈值（0-100，≥ 此值视为同一人） */
        private double matchThreshold = 80;
    }
}