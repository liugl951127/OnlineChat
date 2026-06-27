package com.example.im.kyc;

import com.example.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 身份证 OCR 服务（v2.1.0 Mock + 真实对接开关）
 *
 * <p>真实生产应替换为：
 * <ul>
 *   <li>百度 OCR API（baidu-aip）</li>
 *   <li>阿里云 OCR（aliyun-java-sdk-core + ocr）</li>
 *   <li>腾讯云 OCR（tencentcloud-sdk-java）</li>
 * </ul>
 *
 * <p>配置：{@code kyc.mock.ocr=false} 走真实 API；默认 {@code true} 走 Mock。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    /** 随机数（Mock） */
    private static final Random RND = new Random();

    private final KycMockProperties mockProps;

    /**
     * OCR 识别身份证
     */
    public Map<String, Object> recognize(String frontImgUrl, String backImgUrl) {
        if (mockProps.isOcr()) {
            return recognizeMock(frontImgUrl, backImgUrl);
        }
        return recognizeReal(frontImgUrl, backImgUrl);
    }

    /** Mock 实现：返回固定数据（开发测试用） */
    private Map<String, Object> recognizeMock(String frontImgUrl, String backImgUrl) {
        log.info("[OCR-Mock] 识别身份证 front={} back={}", frontImgUrl, backImgUrl);

        try { Thread.sleep(200 + RND.nextInt(300)); } catch (InterruptedException ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("name", "张三");
        result.put("gender", "M");
        result.put("nation", "汉");
        result.put("birth", "1990-01-15");
        result.put("address", "北京市朝阳区某街道88号");
        result.put("issue", "北京市公安局朝阳分局");
        result.put("validity", "2015.06.01-2035.06.01");
        result.put("cardNo", "110105199001151234");
        result.put("confidence", 0.97);
        return result;
    }

    /** 真实对接：百度 OCR（示例） */
    private Map<String, Object> recognizeReal(String frontImgUrl, String backImgUrl) {
        log.info("[OCR-Real] 调用百度 OCR API front={} back={}", frontImgUrl, backImgUrl);

        // 生产实现示例：
        // AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);
        // JSONObject res = client.idcard(frontImgBytes, backImgBytes, options);
        // Map<String, Object> result = parseBaiduResponse(res);
        //
        // 失败抛出：
        // throw new ApiException(500, "OCR 识别失败：" + res.getString("error_msg"));

        throw new ApiException(501, "OCR 真实 API 未配置：请实现 OcrService.recognizeReal() 或将 kyc.mock.ocr 改为 true");
    }
}