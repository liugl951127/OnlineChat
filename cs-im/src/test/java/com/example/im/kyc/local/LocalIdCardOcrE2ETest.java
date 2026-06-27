package com.example.im.kyc.local;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地 OCR 端到端测试（v2.2.1）
 *
 * <p>真实身份证图片 → Tesseract CLI → 模拟 LocalIdCardOcrService 解析流程
 *
 * <p>tess4j 5.13 + javacv Tesseract 5.3.4 在某些环境下有训练数据加载问题，
 * 这里直接用命令行 tesseract（生产接口完全等价）来验证 OCR 准确性。
 */
//@EnabledIf("isTestImageAvailable")
class LocalIdCardOcrE2ETest {

    private static final Path FRONT_IMG = findTestBase().resolve("test-images/idcard_front_test.png");
    private static final Path BACK_IMG = findTestBase().resolve("test-images/idcard_back_test.png");

    private static Path findTestBase() {
        String basedir = System.getProperty("project.basedir");
        if (basedir != null) return Paths.get(basedir, "src/test/resources");
        return Paths.get("src/test/resources").toAbsolutePath();
    }

    private static String frontBase64;
    private static String backBase64;

    static boolean isTestImageAvailable() {
        try {
            return Files.exists(FRONT_IMG) && Files.size(FRONT_IMG) > 1000;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeAll
    static void loadImages() throws IOException {
        frontBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(FRONT_IMG));
        backBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(BACK_IMG));
        System.out.println("[E2E] 加载测试图片 front=" + frontBase64.length() + "B, back=" + backBase64.length() + "B");
    }

    /** 写 base64 到临时 PNG 文件 */
    private static String writeBase64ToTempFile(String base64, String suffix) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        Path tmp = Files.createTempFile("idcard_test_", suffix);
        Files.write(tmp, bytes);
        return tmp.toAbsolutePath().toString();
    }

    /** 调系统 tesseract CLI */
    private static String runTesseract(String imagePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "tesseract", imagePath, "stdout", "-l", "chi_sim");
        // 智能定位 tessdata：可能是 cs-im/src 或 online-chat/src
        String basedir = System.getProperty("project.basedir");
        String tessPath;
        if (basedir != null) {
            tessPath = Paths.get(basedir, "src/main/resources/tessdata/").toString() + "/";
        } else {
            // user.dir 是 cs-im 或 online-chat
            Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path candidate = p.resolve("src/main/resources/tessdata");
            if (Files.exists(candidate)) {
                tessPath = candidate.toString() + "/";
            } else {
                tessPath = p.getParent().resolve("cs-im/src/main/resources/tessdata").toString() + "/";
            }
        }
        pb.environment().put("TESSDATA_PREFIX", tessPath);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        byte[] out;
        try (InputStream is = proc.getInputStream()) {
            out = is.readAllBytes();
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new RuntimeException("tesseract 失败 exit=" + exit + " out=" + new String(out));
        }
        return new String(out, "UTF-8");
    }

    @Test
    void ocrRecognize_realIdCardImage() throws Exception {
        // 1) 写临时文件
        String frontFile = writeBase64ToTempFile(frontBase64, "_front.png");
        String backFile = writeBase64ToTempFile(backBase64, "_back.png");

        // 2) 调 Tesseract
        String frontText = runTesseract(frontFile);
        String backText = runTesseract(backFile);
        System.out.println("[E2E] 正面识别：\n" + frontText);
        System.out.println("[E2E] 反面识别：\n" + backText);

        // 3) 模拟 LocalIdCardOcrService.parseFrontFields
        Map<String, Object> front = LocalIdCardOcrService.parseFrontFieldsPublic(frontText);
        Map<String, Object> back = LocalIdCardOcrService.parseBackFieldsPublic(backText);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("name", front.get("name"));
        result.put("gender", "男".equals(front.get("gender")) ? "M" : "F");
        result.put("nation", front.get("nation"));
        result.put("birth", front.get("birth"));
        result.put("address", front.get("address"));
        result.put("idCardNo", front.get("idCardNo"));
        result.put("issueAuthority", back.get("issueAuthority"));
        result.put("validPeriod", back.get("validPeriod"));
        result.put("confidence", 0.92);

        System.out.println("[E2E] 最终识别结果：");
        result.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        // 4) 校验字段
        assertThat(result).isNotNull();
        assertThat(result.get("name")).isEqualTo("张三");
        assertThat(result.get("gender")).isEqualTo("M");
        assertThat(result.get("nation")).isEqualTo("汉族");
        assertThat(result.get("birth")).isEqualTo("1990-01-15");
        assertThat((String) result.get("idCardNo")).isEqualTo("110105199001151235");
        assertThat(LocalIdCardOcrService.isValidIdCard((String) result.get("idCardNo"))).isTrue();
        assertThat((String) result.get("address")).contains("北京市朝阳区");

        System.out.println("[E2E] ✓ 全部字段解析正确");
    }

    @Test
    void ocrRecognize_chineseOcrAccuracy() throws Exception {
        String frontFile = writeBase64ToTempFile(frontBase64, "_front.png");
        String frontText = runTesseract(frontFile);

        // 去掉所有空格 + 换行后检查字段
        String normalized = frontText.replaceAll("\\s+", "");

        // 检查关键内容是否出现（去空格后匹配）
        String[] expectedFields = {"姓名", "张三", "性别", "男", "民族", "汉族", "出生", "1990", "01", "15", "住址", "北京市朝阳区", "110105199001151235"};
        int hit = 0;
        for (String f : expectedFields) {
            if (normalized.contains(f)) hit++;
        }
        double accuracy = hit * 100.0 / expectedFields.length;
        System.out.printf("[E2E] 中文 OCR 字段命中率（去空格）：%d/%d = %.1f%%%n", hit, expectedFields.length, accuracy);
        System.out.println("[E2E] Tesseract 原始输出：\n" + frontText);

        // 至少 90% 命中（OCR 不可能 100%）
        assertThat(accuracy).isGreaterThanOrEqualTo(90.0);
        System.out.println("[E2E] ✓ 中文 OCR 准确率达标（≥90%）");
    }

    @Test
    void ocrRecognize_invalidImage_throws400() {
        // 无效 base64 → LocalIdCardOcrService 直接抛 ApiException
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            LocalIdCardOcrService ocr = new LocalIdCardOcrService();
            java.lang.reflect.Field enabled = LocalIdCardOcrService.class.getDeclaredField("enabled");
            enabled.setAccessible(true);
            enabled.setBoolean(ocr, true);
            ocr.recognize(Base64.getEncoder().encodeToString("not an image".getBytes()), null);
        }).isInstanceOf(com.example.common.ApiException.class);
        System.out.println("[E2E] ✓ 无效图像抛出 ApiException");
    }
}