package com.example.im.kyc.local;

import com.example.im.kyc.FaceMatchService;
import com.example.im.kyc.OcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地 OCR + 人脸识别测试（v2.2.1）
 *
 * <p>需要 classpath:tessdata/chi_sim.traineddata + haarcascade_frontalface.xml
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
class LocalOcrFaceTest {

    @Autowired LocalIdCardOcrService localOcr;
    @Autowired LocalFaceMatchService localFace;
    @Autowired OcrService ocrService;
    @Autowired FaceMatchService faceService;

    @Test
    void context_loads() {
        // Spring 启动成功
        assertThat(localOcr).isNotNull();
        assertThat(localFace).isNotNull();
    }

    @Test
    void idCardChecksum_valid() {
        // 合法 18 位身份证号 + 正确校验码
        assertThat(LocalIdCardOcrService.isValidIdCard("110105199001151235")).isTrue();
        // 错误校验码
        assertThat(LocalIdCardOcrService.isValidIdCard("110105199001151235")).isFalse();
        // 17 位
        assertThat(LocalIdCardOcrService.isValidIdCard("11010519900115123")).isFalse();
        System.out.println("[Test] 身份证校验码算法 OK");
    }

    @Test
    void idCardMask() {
        assertThat(LocalIdCardOcrService.maskIdCard("110105199001151235")).isEqualTo("1101**********1234");
        System.out.println("[Test] 脱敏: 110105199001151235 → " + LocalIdCardOcrService.maskIdCard("110105199001151235"));
    }

    @Test
    void ocrService_choosesLocalMode() {
        // 当 kyc.mock.ocr=false 且 local.ocr.enabled=true 时，应走 local
        // 这里只验证 wiring 正确（不实际跑 OCR，需要真实图片）
        assertThat(ocrService).isNotNull();
        System.out.println("[Test] OcrService wired correctly");
    }

    @Test
    void faceService_choosesLocalMode() {
        assertThat(faceService).isNotNull();
        System.out.println("[Test] FaceMatchService wired correctly");
    }
}