package com.example.im;

import com.example.im.domain.BankCard;
import com.example.im.domain.KycApplication;
import com.example.im.domain.KycRiskStatement;
import com.example.im.service.KycService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KYC 集成测试（v2.0.0）
 * 真实 MySQL + Redis + Kafka 环境
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class KycIntegrationTest {

    @Autowired private KycService kycService;

    @Test
    void testKycFullFlow() {
        String customerId = "real_kyc-buyer-" + System.nanoTime();

        // 1) 创建申请单
        KycApplication app = kycService.createApplication(customerId);
        assertEquals("INIT", app.getStatus());
        assertNotNull(app.getApplicationNo());

        // 2) 身份证 OCR
        app = kycService.uploadIdCard(app.getApplicationNo(),
                "https://oss.example.com/idcard/front.jpg",
                "https://oss.example.com/idcard/back.jpg");
        assertEquals("OCR_UPLOADED", app.getStatus());
        assertEquals("张三", app.getIdCardName());

        // 3) 活体检测
        app = kycService.checkLiveness(app.getApplicationNo(),
                "https://oss.example.com/face.jpg",
                Arrays.asList("MOUTH", "BLINK", "SHAKE"));
        assertTrue("LIVENESS_PASSED".equals(app.getStatus()) || "REJECTED".equals(app.getStatus()));
        // Mock 90% 通过，可能 reject

        if ("LIVENESS_PASSED".equals(app.getStatus())) {
            // 4) 人脸比对
            app = kycService.matchFace(app.getApplicationNo());
            assertTrue("FACE_MATCHED".equals(app.getStatus()) || "REJECTED".equals(app.getStatus()));

            if ("FACE_MATCHED".equals(app.getStatus())) {
                // 5) 视频双录
                app = kycService.submitVideo(app.getApplicationNo(), "RS-INVEST-001",
                        "mock-base64-video-content", 20);
                assertEquals("VIDEO_RECORDED", app.getStatus());

                // 6) 提交审核
                app = kycService.submitForAudit(app.getApplicationNo());
                assertEquals("AUDITING", app.getStatus());

                // 7) 审核通过
                app = kycService.approve(app.getApplicationNo(), "auditor01",
                        new BigDecimal("95.0"), "审核通过");
                assertEquals("APPROVED", app.getStatus());

                // 8) 绑卡
                app = kycService.bindBankCard(app.getApplicationNo(),
                        "6222021234567890123", "张三",
                        "110105199001151234", "13800138000");
                assertEquals("COMPLETED", app.getStatus());
                assertNotNull(app.getCompletedAt());

                // 验证 isCompleted
                assertTrue(kycService.isCompleted(customerId));
            }
        }
    }

    @Test
    void testRiskStatementList() {
        List<KycRiskStatement> stmts = kycService.listRiskStatements();
        assertTrue(stmts.size() >= 5, "应有至少 5 段风险声明");
    }

    @Test
    void testKycStatus() {
        String status = kycService.getCustomerKycStatus("nonexistent-customer");
        assertEquals("NOT_STARTED", status);
    }

    @Test
    void testKycRejection() {
        String customerId = "kyc-test-reject-" + System.nanoTime();

        // 创建申请
        KycApplication app = kycService.createApplication(customerId);

        // 上传身份证（Mock 通过）
        app = kycService.uploadIdCard(app.getApplicationNo(),
                "https://oss.example.com/idcard/front.jpg",
                "https://oss.example.com/idcard/back.jpg");

        // 视频双录（Mock 通过）
        if ("OCR_UPLOADED".equals(app.getStatus())) {
            app = kycService.checkLiveness(app.getApplicationNo(),
                    "https://oss.example.com/face.jpg",
                    Arrays.asList("MOUTH", "BLINK"));
            if ("LIVENESS_PASSED".equals(app.getStatus())) {
                app = kycService.matchFace(app.getApplicationNo());
                if ("FACE_MATCHED".equals(app.getStatus())) {
                    app = kycService.submitVideo(app.getApplicationNo(), "RS-INVEST-001",
                            "mock-base64", 15);
                    if ("VIDEO_RECORDED".equals(app.getStatus())) {
                        app = kycService.submitForAudit(app.getApplicationNo());
                        app = kycService.reject(app.getApplicationNo(), "auditor01", "视频未通过审");
                        assertEquals("REJECTED", app.getStatus());
                        assertEquals("auditor01", app.getAuditorUsername());
                    }
                }
            }
        }
    }
}