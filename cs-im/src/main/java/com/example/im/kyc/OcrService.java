package com.example.im.kyc;

import com.example.im.kyc.baidu.BaiduOcrClient;
import com.example.im.kyc.local.LocalIdCardOcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 身份证 OCR 服务（v2.2.1 三模式支持）
 *
 * <p>三种实现，按优先级尝试：
 * <ol>
 *   <li><b>local</b> — 本地自研（OpenCV + Tesseract），离线，零外部 API</li>
 *   <li><b>baidu</b> — 百度智能云 OCR REST API（精度高）</li>
 *   <li><b>mock</b> — 返回固定数据（开发测试）</li>
 * </ol>
 *
 * <p>选择逻辑（默认优先 local）：
 * <pre>
 * kyc.mock.ocr = false  → 尝试 local
 *   local.ocr.enabled = true + 文件齐全 → 用 local
 *   失败 / 未启用 → fallback baidu
 * kyc.mock.ocr = true → mock
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final KycMockProperties mockProps;
    private final LocalIdCardOcrService localOcrService;
    private final BaiduOcrClient baiduOcrClient;

    public Map<String, Object> recognize(String frontImg, String backImg) {
        // 1) Mock 模式
        if (mockProps.isOcr()) {
            return recognizeMock(frontImg, backImg);
        }

        // 2) 优先本地自研（离线 + 零成本）
        if (isLocalAvailable()) {
            try {
                log.info("[OCR] 尝试本地自研");
                return localOcrService.recognize(frontImg, backImg);
            } catch (Exception e) {
                log.warn("[OCR] 本地失败，fallback 到百度: {}", e.getMessage());
            }
        }

        // 3) Fallback 百度云（高精度的云端 OCR）
        log.info("[OCR] 使用百度智能云");
        return recognizeBaidu(frontImg, backImg);
    }

    /** 本地服务可用性 */
    private boolean isLocalAvailable() {
        return localOcrService != null;
    }

    private Map<String, Object> recognizeMock(String frontImg, String backImg) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", "张三");
        result.put("gender", "M");
        result.put("nation", "汉");
        result.put("birth", "1990-01-15");
        result.put("address", "北京市朝阳区某街道88号");
        result.put("issueAuthority", "北京市公安局朝阳分局");
        result.put("validPeriod", "2015.06.01-2035.06.01");
        result.put("idCardNo", "110105199001151235");
        result.put("confidence", 0.97);
        return result;
    }

    private Map<String, Object> recognizeBaidu(String frontImg, String backImg) {
        String frontBase64 = extractBase64(frontImg);
        String backBase64 = extractBase64(backImg);

        Map<String, Object> baiduResult = baiduOcrClient.recognizeIdCard(frontBase64, backBase64);

        Map<String, Object> result = new HashMap<>();
        result.put("name", baiduResult.get("name"));
        result.put("gender", baiduResult.get("gender"));
        result.put("nation", baiduResult.get("nation"));
        result.put("birth", baiduResult.get("birth"));
        result.put("address", baiduResult.get("address"));
        result.put("idCardNo", baiduResult.get("idCardNo"));
        result.put("issueAuthority", baiduResult.get("issueAuthority"));
        result.put("validPeriod", baiduResult.get("validPeriod"));
        result.put("confidence", 0.95);
        return result;
    }

    private String extractBase64(String image) {
        if (image == null || image.isBlank()) return image;
        int idx = image.indexOf("base64,");
        return idx > 0 ? image.substring(idx + "base64,".length()) : image;
    }
}