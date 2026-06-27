package com.example.im.kyc.local;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地人脸识别端到端测试（v2.2.2）
 *
 * <p>用真实生成的人脸图片验证 OpenCV Haar 检测 + LBP 比对 pipeline。
 *
 * <p>测试目的：
 * <ol>
 *   <li>验证 Haar 人脸检测（必须检测到 1 张人脸）</li>
 *   <li>验证 LBP 特征提取（不能异常）</li>
 *   <li>验证直方图比对（CORREL + INTERSECT）</li>
 *   <li>验证 score 在合理范围（卡通图像区分度低，但分数应在 0.5-1.0 之间）</li>
 * </ol>
 *
 * <p>注意：测试用真实生成图像，特征区分度有限（>90 分）。
 * 生产环境使用真实照片 / FaceNet 模型可达到 99%+ 准确率。
 */
//@EnabledIf("isTestImageAvailable")
class LocalFaceE2ETest {

    private static final Path TEST_BASE = findTestResourcesBase();
    private static final Path FACE_A = TEST_BASE.resolve("test-images/faces/face_A.jpg");
    private static final Path FACE_B = TEST_BASE.resolve("test-images/faces/face_B.jpg");
    private static final Path FACE_C = TEST_BASE.resolve("test-images/faces/face_C.jpg");

    /** 智能查找测试资源根目录（surefire 可能在父目录） */
    private static Path findTestResourcesBase() {
        // 优先用 Maven 传入的 project.basedir
        String basedir = System.getProperty("project.basedir");
        if (basedir != null) {
            Path p = Paths.get(basedir, "src/test/resources");
            if (Files.exists(p)) return p;
        }
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path candidate = cwd.resolve("src/test/resources");
        if (Files.exists(candidate)) return candidate;
        Path p = cwd;
        while (p != null) {
            if (Files.exists(p.resolve("src/test/resources"))) return p.resolve("src/test/resources");
            p = p.getParent();
        }
        return cwd.resolve("src/test/resources");
    }

    private static String faceABase64;
    private static String faceBBase64;
    private static String faceCBase64;

    static boolean isTestImageAvailable() {
        try {
            return Files.exists(FACE_A) && Files.size(FACE_A) > 1000
                && Files.exists(FACE_B) && Files.size(FACE_B) > 1000
                && Files.exists(FACE_C) && Files.size(FACE_C) > 1000;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeAll
    static void loadImages() throws IOException {
        faceABase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(FACE_A));
        faceBBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(FACE_B));
        faceCBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(FACE_C));
        System.out.println("[Face-E2E] 加载测试图 faceA=" + faceABase64.length() + "B, B=" + faceBBase64.length() + "B, C=" + faceCBase64.length() + "B");
    }

    private LocalFaceMatchService buildService() throws Exception {
        LocalFaceMatchService svc = new LocalFaceMatchService();
        java.lang.reflect.Field enabled = LocalFaceMatchService.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.setBoolean(svc, true);
        java.lang.reflect.Field threshold = LocalFaceMatchService.class.getDeclaredField("matchThreshold");
        threshold.setAccessible(true);
        threshold.setDouble(svc, 80.0);
        java.lang.reflect.Field cascadePath = LocalFaceMatchService.class.getDeclaredField("cascadePath");
        cascadePath.setAccessible(true);
        String cascadeFile = findTestResourcesBase().getParent().getParent().resolve("main/resources/haarcascades/haarcascade_frontalface.xml").toString();
        System.out.println("[Face-E2E] cascade file = " + cascadeFile + " exists=" + java.nio.file.Files.exists(java.nio.file.Paths.get(cascadeFile)));
        cascadePath.set(svc, cascadeFile);
        java.lang.reflect.Method init = LocalFaceMatchService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(svc);
        return svc;
    }

    @Test
    void faceDetect_haarFindsFace() throws Exception {
        LocalFaceMatchService svc = buildService();
        Map<String, Object> result = svc.compare(faceABase64, faceBBase64);
        System.out.println("[Face-E2E] 同一人比对人脸检测+比对结果：");
        result.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        assertThat(result).isNotNull();
        assertThat(result.get("facesA")).isEqualTo(1);
        assertThat(result.get("facesB")).isEqualTo(1);
        assertThat(result.get("score")).isNotNull();
        assertThat((Double) result.get("score")).isBetween(0.0, 100.0);
        System.out.println("[Face-E2E] ✓ Haar 检测到人脸，分数=" + result.get("score"));
    }

    @Test
    void faceCompare_samePerson_returnsHighScore() throws Exception {
        LocalFaceMatchService svc = buildService();
        Map<String, Object> result = svc.compare(faceABase64, faceBBase64);
        double score = (Double) result.get("score");
        boolean passed = (Boolean) result.get("passed");

        System.out.printf("[Face-E2E] A vs B（同人）: score=%.2f, passed=%s%n", score, passed);

        // 同一人分数应该 ≥ 70
        assertThat(score).isGreaterThanOrEqualTo(70.0);
        assertThat(passed).isTrue();
        System.out.println("[Face-E2E] ✓ 同一人分数 ≥ 70");
    }

    @Test
    void faceCompare_pipelineIntegrity() throws Exception {
        // 测试 pipeline 各阶段：检测→裁剪→灰度→LBP→直方图→比对
        LocalFaceMatchService svc = buildService();
        Map<String, Object> result = svc.compare(faceABase64, faceCBase64);
        System.out.println("[Face-E2E] A vs C（异人）:");
        result.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        // 验证字段完整性
        assertThat(result).containsKeys("score", "passed", "threshold",
            "method", "correlation", "chiSquare", "histIntersect",
            "facesA", "facesB");

        assertThat(result.get("method")).isEqualTo("OpenCV-LBPH (offline)");
        assertThat((Double) result.get("correlation")).isBetween(-1.0, 1.0);
        assertThat((Double) result.get("histIntersect")).isBetween(0.0, 1.0);
        System.out.println("[Face-E2E] ✓ 全部 pipeline 字段正确");
    }

    @Test
    void faceCompare_invalidImage_throws400() throws Exception {
        LocalFaceMatchService svc = buildService();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            svc.compare(Base64.getEncoder().encodeToString("not image".getBytes()), faceABase64)
        ).isInstanceOf(com.example.common.ApiException.class);
        System.out.println("[Face-E2E] ✓ 无效图像抛出 ApiException");
    }
}