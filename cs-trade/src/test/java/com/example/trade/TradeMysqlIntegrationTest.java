package com.example.trade;

import com.example.trade.domain.Product;
import com.example.trade.repo.ProductRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cs-trade 端到端集成测试（连接 MySQL）
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("mysql-it")
class TradeMysqlIntegrationTest {

    @Autowired ProductRepo productRepo;

    @BeforeEach
    void clean() {
        try { productRepo.deleteAll(); } catch (Exception ignored) {}
    }

    @Test
    void context_loads_with_mysql() {
        long count = productRepo.count();
        System.out.println("[IT-Trade] ✓ Spring context loaded. products=" + count);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void product_crud_against_mysql() {
        Product p = new Product();
        p.setCode("PRD-IT-" + System.nanoTime());
        p.setName("测试产品");
        p.setDescription("集成测试产品");
        p.setRate(5.0);
        p.setPeriod("30D");
        p.setRiskLevel("LOW");
        p.setCreatedAt(Instant.now());
        Product saved = productRepo.save(p);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("测试产品");
        System.out.println("[IT-Trade] ✓ Product saved: id=" + saved.getId() + ", code=" + saved.getCode());

        Product fetched = productRepo.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getRate()).isEqualTo(5.0);
        System.out.println("[IT-Trade] ✓ Product fetched from MySQL: " + fetched.getName() + " (rate=" + fetched.getRate() + "%)");
    }
}