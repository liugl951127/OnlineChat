package com.example.im.kyc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 身份证 OCR 服务（v2.0.0 Mock 实现）
 *
 * <p>真实生产应替换为：
 * <ul>
 *   <li>百度 OCR API（baidu-aip）</li>
 *   <li>阿里云 OCR（aliyun-java-sdk-core + ocr）</li>
 *   <li>腾讯云 OCR（tencentcloud-sdk-java）</li>
 * </ul>
 *
 * <p>接口签名保持一致，方便后续无缝切换。
 */
@Slf4j
@Service
public class OcrService {

    /** 随机数（Mock） */
    private static final Random RND = new Random();

    /**
     * OCR 识别身份证
     *
     * @param frontImgUrl 身份证正面图 URL（Base64 或 OSS URL）
     * @param backImgUrl  身份证反面图 URL
     * @return 识别结果 { name, gender, nation, birth, address, issue, validity, cardNo }
     */
    public Map<String, Object> recognize(String frontImgUrl, String backImgUrl) {
        log.info("[OCR-Mock] 识别身份证 front={} back={}", frontImgUrl, backImgUrl);

        // 1) 模拟 OCR 处理耗时
        try { Thread.sleep(200 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        // 2) 返回 Mock 结果（生产由真实 OCR 替换）
        Map<String, Object> result = new HashMap<>();
        result.put("name", "张三");
        result.put("gender", "M");
        result.put("nation", "汉");
        result.put("birth", "1990-01-15");
        result.put("address", "北京市朝阳区某街道88号");
        result.put("issue", "北京市公安局朝阳分局");
        result.put("validity", "2015.06.01-2035.06.01");
        result.put("cardNo", "110105199001151234");
        result.put("confidence", 0.97);  // 置信度

        log.info("[OCR-Mock] 识别结果: {}", result);
        return result;
    }
}