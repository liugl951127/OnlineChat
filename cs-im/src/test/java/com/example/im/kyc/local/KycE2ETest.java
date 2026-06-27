package com.example.im.kyc.local;

import com.example.im.kyc.BankCardVerifyService;
import com.example.im.kyc.FaceMatchService;
import com.example.im.kyc.LivenessService;
import com.example.im.kyc.OcrService;
import com.example.im.repo.BankCardMapper;
import com.example.im.repo.KycApplicationMapper;
import com.example.im.domain.BankCard;
import com.example.im.domain.KycApplication;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC 端到端测试（v2.2.2）
 *
 * <p>完整模拟客户 KYC 7 步流程 → 全部入库
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

class KycE2ETest {

    @Autowired OcrService ocrService;
    @Autowired FaceMatchService faceMatchService;
    @Autowired LivenessService livenessService;
    @Autowired BankCardVerifyService bankCardVerifyService;

    @Autowired KycApplicationMapper kycApplicationMapper;
    @Autowired BankCardMapper bankCardMapper;

    @MockBean SimpMessagingTemplate messagingTemplate;

    private static final Path FRONT_IMG = findTestBase().resolve("test-images/idcard_front_test.png");
    private static final Path BACK_IMG = findTestBase().resolve("test-images/idcard_back_test.png");

    private static Path findTestBase() {
        String basedir = System.getProperty("project.basedir");
        if (basedir != null) return Paths.get(basedir, "src/test/resources");
        // 尝试 user.dir 智能探测：可能是 cs-im 或 online-chat
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path candidate = p.resolve("src/test/resources");
        if (Files.exists(candidate)) return candidate;
        return p.getParent().resolve("cs-im/src/test/resources");
    }

    private static String frontBase64;
    private static String backBase64;

    static boolean isIdCardTestImageAvailable() {
        try {
            return Files.exists(FRONT_IMG) && Files.size(FRONT_IMG) > 1000;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeAll
    static void load() throws IOException {
        frontBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(FRONT_IMG));
        backBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(BACK_IMG));
    }

    @Test
    @Order(1)
    void step1_ocrRecognize_realIdCard() {
        // 1) OCR 识别
        Map<String, Object> ocrResult = ocrService.recognize(frontBase64, backBase64);
        System.out.println("[KYC-E2E] Step 1 - OCR 识别结果：");
        ocrResult.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        assertThat(ocrResult).isNotNull();
        assertThat(ocrResult.get("name")).isEqualTo("张三");
        assertThat((String) ocrResult.get("idCardNo")).isEqualTo("110105199001151235");
        assertThat(LocalIdCardOcrService.isValidIdCard((String) ocrResult.get("idCardNo"))).isTrue();
        System.out.println("[KYC-E2E] ✓ 步骤1: OCR 识别成功");
    }

    @Test
    @Order(2)
    void step2_createKycApplication_inDatabase() {
        String customerId = "e2e-customer-" + System.currentTimeMillis();
        String applicationNo = "KYC-E2E-" + System.currentTimeMillis();

        KycApplication app = new KycApplication();
        app.setApplicationNo(applicationNo);
        app.setCustomerId(customerId);
        app.setStatus("INIT");
        kycApplicationMapper.insert(app);

        KycApplication fromDb = kycApplicationMapper.selectOne(
            new QueryWrapper<KycApplication>().eq("application_no", applicationNo));
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getStatus()).isEqualTo("INIT");
        System.out.println("[KYC-E2E] ✓ 步骤2: 创建 KYC 申请入库 (id=" + fromDb.getId() + ")");
    }

    @Test
    @Order(3)
    void step3_updateToOcrUploaded() {
        String applicationNo = createTestApplication("step3");
        KycApplication app = kycApplicationMapper.selectOne(
            new QueryWrapper<KycApplication>().eq("application_no", applicationNo));
        app.setIdCardName("张三");
        app.setIdCardGender("M");
        app.setIdCardNation("汉族");
        app.setIdCardNo("110105199001151235");
        app.setStatus("OCR_UPLOADED");
        kycApplicationMapper.updateById(app);

        KycApplication updated = kycApplicationMapper.selectById(app.getId());
        assertThat(updated.getStatus()).isEqualTo("OCR_UPLOADED");
        assertThat(updated.getIdCardName()).isEqualTo("张三");
        System.out.println("[KYC-E2E] ✓ 步骤3: OCR 状态更新入库");
    }

    @Test
    @Order(4)
    void step4_livenessCheck() {
        Map<String, Object> liveness = livenessService.check(Arrays.asList("MOUTH", "BLINK", "SHAKE", "NOD"));
        System.out.println("[KYC-E2E] Step 4 - 活体检测：");
        liveness.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        assertThat(liveness.get("passed")).isEqualTo(true);
        System.out.println("[KYC-E2E] ✓ 步骤4: 活体检测通过");
    }

    @Test
    @Order(5)
    void step5_faceMatch() {
        Map<String, Object> match = faceMatchService.compare(frontBase64, frontBase64);
        System.out.println("[KYC-E2E] Step 5 - 人脸比对（自比对）：");
        match.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        assertThat((Double) match.get("score")).isGreaterThanOrEqualTo(90.0);
        System.out.println("[KYC-E2E] ✓ 步骤5: 人脸比对通过");
    }

    @Test
    @Order(6)
    void step6_bindBankCard_fourElements() {
        Map<String, Object> verify = bankCardVerifyService.verify4Elements(
            "6222021234567890123", "张三",
            "110105199001151235", "13800138000");

        System.out.println("[KYC-E2E] Step 6 - 银行卡四要素：");
        verify.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        assertThat(verify.get("verified")).isEqualTo(true);

        BankCard card = new BankCard();
        String bankCust = "e2e-customer-step6-" + System.currentTimeMillis();
        card.setCustomerId(bankCust);
        card.setCardNoMasked("6222***********0123");
        card.setCardName("张三");
        card.setCardType("DEBIT");
        card.setMobile("13800138000");
        card.setVerified(1);
        card.setIsDefault(1);
        bankCardMapper.insert(card);

        BankCard fromDb = bankCardMapper.selectOne(
            new QueryWrapper<BankCard>().eq("customer_id", bankCust));
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getVerified()).isEqualTo(1);
        System.out.println("[KYC-E2E] ✓ 步骤6: 银行卡鉴权 + 入库 (id=" + fromDb.getId() + ")");
    }

    @Test
    @Order(7)
    void step7_completeWorkflow_ocrToBankCardBinding() {
        String customerId = "e2e-full-" + System.currentTimeMillis();
        String applicationNo = "KYC-FULL-" + System.currentTimeMillis();

        // Step 1: OCR
        Map<String, Object> ocrResult = ocrService.recognize(frontBase64, backBase64);
        assertThat(ocrResult.get("name")).isEqualTo("张三");

        // Step 2: 创建申请
        KycApplication app = new KycApplication();
        app.setApplicationNo(applicationNo);
        app.setCustomerId(customerId);
        app.setIdCardName((String) ocrResult.get("name"));
        app.setIdCardGender((String) ocrResult.get("gender"));
        app.setIdCardNation((String) ocrResult.get("nation"));
        app.setIdCardNo((String) ocrResult.get("idCardNo"));
        app.setStatus("INIT");
        kycApplicationMapper.insert(app);

        // Step 3: OCR 完成
        app.setStatus("OCR_UPLOADED");
        app.setIdCardAddress((String) ocrResult.get("address"));
        app.setIdCardIssue((String) ocrResult.get("issueAuthority"));
        app.setIdCardValidity((String) ocrResult.get("validPeriod"));
        kycApplicationMapper.updateById(app);

        // Step 4: 活体
        Map<String, Object> liveness = livenessService.check(Arrays.asList("MOUTH", "BLINK", "SHAKE"));
        assertThat(liveness.get("passed")).isEqualTo(true);
        app.setStatus("LIVENESS_PASSED");
        app.setLivenessScore(BigDecimal.valueOf((Double) liveness.get("score")));
        app.setLivenessPassed(1);
        kycApplicationMapper.updateById(app);

        // Step 5: 人脸
        Map<String, Object> faceMatch = faceMatchService.compare(frontBase64, frontBase64);
        app.setStatus("FACE_MATCHED");
        app.setFaceMatchScore(BigDecimal.valueOf((Double) faceMatch.get("score")));
        app.setFaceMatchPassed(1);
        kycApplicationMapper.updateById(app);

        // Step 6: 视频双录
        app.setStatus("VIDEO_RECORDED");
        kycApplicationMapper.updateById(app);

        // Step 7: 提交审核 → 通过
        app.setStatus("AUDITING");
        kycApplicationMapper.updateById(app);
        app.setStatus("APPROVED");
        app.setAuditorUsername("agent-test");
        app.setAuditedAt(java.time.LocalDateTime.now());
        kycApplicationMapper.updateById(app);

        // Step 8: 绑卡
        app.setStatus("BANK_BINDING");
        kycApplicationMapper.updateById(app);
        BankCard card = new BankCard();
        card.setCustomerId(customerId);
        card.setCardNoMasked("6222***********0123");
        card.setCardName((String) ocrResult.get("name"));
        card.setCardType("DEBIT");
        card.setVerified(1);
        card.setIsDefault(1);
        bankCardMapper.insert(card);

        // 完成
        app.setStatus("COMPLETED");
        app.setCompletedAt(java.time.LocalDateTime.now());
        kycApplicationMapper.updateById(app);

        // 验证
        KycApplication finalApp = kycApplicationMapper.selectOne(
            new QueryWrapper<KycApplication>().eq("application_no", applicationNo));
        BankCard finalCard = bankCardMapper.selectOne(
            new QueryWrapper<BankCard>().eq("customer_id", customerId));

        assertThat(finalApp.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalApp.getIdCardName()).isEqualTo("张三");
        assertThat(finalApp.getIdCardNo()).isEqualTo("110105199001151235");
        assertThat(finalApp.getLivenessScore()).isNotNull();
        assertThat(finalApp.getFaceMatchScore()).isNotNull();
        assertThat(finalApp.getFaceMatchPassed()).isEqualTo(1);
        assertThat(finalApp.getAuditorUsername()).isEqualTo("agent-test");
        assertThat(finalApp.getAuditedAt()).isNotNull();
        assertThat(finalApp.getCompletedAt()).isNotNull();
        assertThat(finalCard).isNotNull();
        assertThat(finalCard.getVerified()).isEqualTo(1);

        System.out.println("[KYC-E2E] ✓✓✓ 完整工作流 7 步全通过！");
        System.out.println("    申请号: " + applicationNo);
        System.out.println("    最终状态: " + finalApp.getStatus());
        System.out.println("    OCR 姓名: " + finalApp.getIdCardName());
        System.out.println("    身份证号: " + finalApp.getIdCardNo());
        System.out.println("    活体分: " + finalApp.getLivenessScore());
        System.out.println("    人脸分: " + finalApp.getFaceMatchScore());
        System.out.println("    绑卡: " + finalCard.getCardNoMasked());
    }

    private String createTestApplication(String tag) {
        String applicationNo = "KYC-" + tag + "-" + System.currentTimeMillis();
        KycApplication app = new KycApplication();
        app.setApplicationNo(applicationNo);
        app.setCustomerId("e2e-customer-" + tag);
        app.setStatus("INIT");
        kycApplicationMapper.insert(app);
        return applicationNo;
    }
}