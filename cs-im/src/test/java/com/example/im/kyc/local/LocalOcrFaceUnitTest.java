package com.example.im.kyc.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地 OCR + 人脸识别 - 纯单元测试（无需 Spring context）
 *
 * <p>验证核心算法逻辑：
 * <ul>
 *   <li>身份证 18 位校验码</li>
 *   <li>字段脱敏</li>
 * </ul>
 */
class LocalOcrFaceUnitTest {

    @Test
    void idCardChecksum_validCases() {
        // 真实合法身份证号（计算正确的校验码）
        assertThat(LocalIdCardOcrService.isValidIdCard("110105199001151235")).isTrue();
        System.out.println("[Unit] ✓ 合法身份证号校验通过");
    }

    @Test
    void idCardChecksum_invalidCases() {
        assertThat(LocalIdCardOcrService.isValidIdCard("110105199001151234")).isFalse();  // 校验码错
        assertThat(LocalIdCardOcrService.isValidIdCard("12345")).isFalse();
        assertThat(LocalIdCardOcrService.isValidIdCard(null)).isFalse();
        assertThat(LocalIdCardOcrService.isValidIdCard("11010519900115123")).isFalse();   // 17 位
        assertThat(LocalIdCardOcrService.isValidIdCard("1101051990011512345")).isFalse(); // 19 位
        System.out.println("[Unit] ✓ 非法身份证号拦截");
    }

    @Test
    void idCardMask() {
        assertThat(LocalIdCardOcrService.maskIdCard("110105199001151235")).isEqualTo("1101**********1235");
        assertThat(LocalIdCardOcrService.maskIdCard(null)).isEqualTo("****");
        assertThat(LocalIdCardOcrService.maskIdCard("12345")).isEqualTo("****");
        System.out.println("[Unit] ✓ 脱敏: " + LocalIdCardOcrService.maskIdCard("110105199001151235"));
    }

    @Test
    void idCardChecksum_demoData() {
        // 默认演示数据
        assertThat(LocalIdCardOcrService.isValidIdCard("110105199001151235")).isTrue();
        System.out.println("[Unit] ✓ 演示数据 110105199001151235 校验码正确");
    }
}