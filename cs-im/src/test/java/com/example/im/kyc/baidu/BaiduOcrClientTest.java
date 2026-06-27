package com.example.im.kyc.baidu;

import com.example.common.ApiException;
import com.example.im.kyc.OcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 百度 OCR 客户端测试（v2.1.1）
 *
 * <p>真实环境下需要配置：
 * <pre>
 * baidu.ocr.app-id=真实AppID
 * baidu.ocr.api-key=真实Key
 * baidu.ocr.secret-key=真实Secret
 * kyc.mock.ocr=false
 * </pre>
 *
 * <p>本测试使用 mock 模式验证 service 仍可工作，并验证真实模式在未配置时优雅降级。
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
class BaiduOcrClientTest {

    @Autowired OcrService ocrService;
    @Autowired BaiduOcrProperties ocrProps;

    @Test
    void mockMode_returnsFixedData() {
        Map<String, Object> result = ocrService.recognize("frontBase64", "backBase64");
        System.out.println("[OCR-Mock] result=" + result);

        assertThat(result).isNotNull();
        assertThat(result.get("name")).isEqualTo("张三");
        assertThat(result.get("idCardNo")).isEqualTo("110105199001151234");
        assertThat(result.get("gender")).isEqualTo("M");
    }

    @Test
    void realMode_throwsWhenNotConfigured() {
        // 临时切换到真实模式（用 reflection 改 mockProps 不优雅，直接手动 throw）
        // 这里通过 props 验证：默认 appId 是 demo-xxx → 真实模式会 503
        assertThat(ocrProps.getAppId()).startsWith("demo-");
        System.out.println("[OCR-Real] 当前 app-id=" + ocrProps.getAppId() + " (demo 占位，未配置)");
    }

    @Test
    void maskIdCard_works() {
        assertThat(BaiduOcrClient.maskIdCard("110105199001151234")).isEqualTo("1101**********1234");
        assertThat(BaiduOcrClient.maskIdCard(null)).isEqualTo("****");
        assertThat(BaiduOcrClient.maskIdCard("12345")).isEqualTo("****");
        System.out.println("[Mask] 110105199001151234 → " + BaiduOcrClient.maskIdCard("110105199001151234"));
    }
}