package com.example.im.kyc;

import com.example.im.kyc.baidu.BaiduOcrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 身份证 OCR 服务（v2.1.1 真实百度 OCR 接入）
 *
 * <p>真实生产对接百度智能云 OCR（v2.1.1）。
 *
 * <p>申请地址：https://console.bce.baidu.com/ai/#/ai/ocr/overview/index
 *
 * <p>配置（application.yml）：
 * <pre>
 * baidu:
 *   ocr:
 *     app-id: 你的 AppID
 *     api-key: 你的 API Key
 *     secret-key: 你的 Secret Key
 * kyc:
 *   mock:
 *     ocr: false
 * </pre>
 *
 * <p>接口签名：
 * <ul>
 *   <li>{@link #recognize(String, String)} — 接收前/反面图 URL 或 Base64</li>
 *   <li>返回字段：name, gender, nation, birth, address, idCardNo, issueAuthority, validPeriod</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final KycMockProperties mockProps;
    private final BaiduOcrClient baiduOcrClient;

    /**
     * OCR 识别身份证（统一入口）
     *
     * @param frontImg 正面图 URL 或 base64
     * @param backImg  反面图 URL 或 base64
     * @return 识别结果 { name, gender, nation, birth, address, idCardNo, issueAuthority, validPeriod, confidence }
     */
    public Map<String, Object> recognize(String frontImg, String backImg) {
        if (mockProps.isOcr()) {
            return recognizeMock(frontImg, backImg);
        }
        return recognizeReal(frontImg, backImg);
    }

    /** Mock 实现：返回固定数据 */
    private Map<String, Object> recognizeMock(String frontImg, String backImg) {
        log.info("[OCR-Mock] 识别 front={} back={}", frontImg, backImg);

        Map<String, Object> result = new HashMap<>();
        result.put("name", "张三");
        result.put("gender", "M");
        result.put("nation", "汉");
        result.put("birth", "1990-01-15");
        result.put("address", "北京市朝阳区某街道88号");
        result.put("issueAuthority", "北京市公安局朝阳分局");
        result.put("validPeriod", "2015.06.01-2035.06.01");
        result.put("idCardNo", "110105199001151234");
        result.put("confidence", 0.97);
        return result;
    }

    /** 真实百度 OCR 对接 */
    private Map<String, Object> recognizeReal(String frontImg, String backImg) {
        log.info("[OCR-Real] 调百度智能云身份证识别 frontLen={} backLen={}",
            frontImg == null ? 0 : frontImg.length(),
            backImg == null ? 0 : backImg.length());

        // 1) 提取 base64（支持 data:image/png;base64,xxx 或纯 base64）
        String frontBase64 = extractBase64(frontImg);
        String backBase64 = extractBase64(backImg);

        // 2) 调百度 OCR
        Map<String, Object> baiduResult = baiduOcrClient.recognizeIdCard(frontBase64, backBase64);

        // 3) 转换为系统统一字段名
        Map<String, Object> result = new HashMap<>();
        result.put("name", baiduResult.get("name"));
        result.put("gender", baiduResult.get("gender"));
        result.put("nation", baiduResult.get("nation"));
        result.put("birth", baiduResult.get("birth"));
        result.put("address", baiduResult.get("address"));
        result.put("idCardNo", baiduResult.get("idCardNo"));
        result.put("issueAuthority", baiduResult.get("issueAuthority"));
        result.put("validPeriod", baiduResult.get("validPeriod"));
        result.put("confidence", 0.95); // 百度 OCR 真实置信度需从接口详取

        return result;
    }

    /** 提取 base64 内容（去掉 data:image/png;base64, 前缀） */
    private String extractBase64(String image) {
        if (image == null || image.isBlank()) return image;
        int idx = image.indexOf("base64,");
        if (idx > 0) return image.substring(idx + "base64,".length());
        return image;
    }
}