package com.example.im;

import com.example.im.domain.FinancialOrder;
import com.example.im.domain.Product;
import com.example.im.repo.ProductMapper;
import com.example.im.service.FinancialOrderService;
import com.example.im.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 金融订单集成测试（真实 MySQL + Redis + Kafka）
 * 5 步状态机：createOrder → assessRisk → runCompliance → pay → redeem
 * 4 道合规关：实名 / 风险 / 适当性 / 反洗钱
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public class FinancialOrderIntegrationTest {

    @Autowired private FinancialOrderService orderService;
    @Autowired private RiskAssessmentService riskService;
    @Autowired private ProductMapper productMapper;
    @Autowired private com.example.im.service.KycService kycService;

    @Test
    void testListProducts() {
        List<Product> products = productMapper.findByStatus("ON_SALE");
        assertNotNull(products);
        assertTrue(products.size() >= 5, "应有至少 5 个金融产品（含 V2.0.0 seed）");
    }

    @Test
    void testRiskAssessmentConservative() {
        Map<String, Object> answers = Map.of(
                "age", 0, "income", 0, "experience", 0,
                "preference", 0, "ratio", 0);
        Map<String, Object> ra = riskService.assess("c-test-conservative", answers);
        assertEquals("CONSERVATIVE", ra.get("riskLevel"));
        assertTrue(((Number) ra.get("score")).intValue() < 40);
    }

    @Test
    void testRiskAssessmentAggressive() {
        Map<String, Object> answers = Map.of(
                "age", 2, "income", 2, "experience", 2,
                "preference", 2, "ratio", 2);
        Map<String, Object> ra = riskService.assess("c-test-aggressive", answers);
        assertEquals("AGGRESSIVE", ra.get("riskLevel"));
        assertTrue(((Number) ra.get("score")).intValue() >= 70);
    }

    @Test
    void testFullPurchaseFlow() {
        List<Product> products = productMapper.findByStatus("ON_SALE");
        Product deposit = products.stream()
                .filter(p -> "DEPOSIT".equals(p.getProductType()))
                .findFirst().orElseThrow();

        Map<String, Object> answers = Map.of(
                "age", 1, "income", 1, "experience", 1,
                "preference", 1, "ratio", 1);
        riskService.assess("real_test-buyer", answers);

        // v2.0.0: 先完成 KYC
        ensureKycCompleted("real_test-buyer");

        FinancialOrder order = orderService.createOrder(
                "real_test-buyer", deposit.getProductCode(), 50000.0, "CUSTOMER", null);
        assertEquals("DRAFT", order.getStatus());

        order = orderService.assessRisk(order.getOrderNo());
        assertEquals("RISK_ASSESSED", order.getStatus());

        order = orderService.runCompliance(order.getOrderNo());
        assertEquals("COMPLIANCE_PASSED", order.getStatus());

        order = orderService.pay(order.getOrderNo(), "MOCK_BANK");
        assertEquals("SETTLED", order.getStatus());
        assertNotNull(order.getCompletedAt());
    }

    @Test
    void testAmountBelowMinimum() {
        List<Product> products = productMapper.findByStatus("ON_SALE");
        Product p = products.stream()
                .filter(x -> x.getMinAmount() != null && x.getMinAmount() > 0)
                .findFirst().orElseThrow();
        assertThrows(RuntimeException.class, () ->
                orderService.createOrder("c-test-low", p.getProductCode(), 1.0, "CUSTOMER", null));
    }

    /** 辅助：完成 KYC（Mock 全通过，最多 10 次重试，避开随机失败） */
    private void ensureKycCompleted(String customerId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (kycService.isCompleted(customerId)) return;
            var app = kycService.createApplication(customerId);
            app = kycService.uploadIdCard(app.getApplicationNo(),
                    "https://oss.example.com/idcard/front.jpg",
                    "https://oss.example.com/idcard/back.jpg");
            if (!"OCR_UPLOADED".equals(app.getStatus())) continue;
            app = kycService.checkLiveness(app.getApplicationNo(),
                    "https://oss.example.com/face.jpg",
                    java.util.Arrays.asList("MOUTH", "BLINK", "SHAKE"));
            if (!"LIVENESS_PASSED".equals(app.getStatus())) continue;
            app = kycService.matchFace(app.getApplicationNo());
            if (!"FACE_MATCHED".equals(app.getStatus())) continue;
            app = kycService.submitVideo(app.getApplicationNo(), "RS-INVEST-001", "mock", 15);
            if (!"VIDEO_RECORDED".equals(app.getStatus())) continue;
            app = kycService.submitForAudit(app.getApplicationNo());
            app = kycService.approve(app.getApplicationNo(), "test_auditor",
                    new java.math.BigDecimal("95"), "ok");
            kycService.bindBankCard(app.getApplicationNo(), "6222021234567890123",
                    "张三", "110105199001151234", "13800138000");
            if (kycService.isCompleted(customerId)) return;
        }
        throw new RuntimeException("KYC 重试 10 次仍未完成");
    }
}