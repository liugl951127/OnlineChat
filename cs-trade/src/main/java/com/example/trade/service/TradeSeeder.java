package com.example.trade.service;

import com.example.trade.domain.Bill;
import com.example.trade.domain.Product;
import com.example.trade.repo.BillRepo;
import com.example.trade.repo.ProductRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 测试数据 seed（开发/演示用，生产应删掉）
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TradeSeeder {

    private final ProductRepo productRepo;
    private final BillRepo billRepo;

    @Bean
    CommandLineRunner seed() {
        return args -> {
            // 产品
            if (productRepo.count() == 0) {
                productRepo.saveAll(Arrays.asList(
                        Product.builder().code("P001").name("活期理财 Plus").description("T+0 赎回，稳健收益").rate(2.85).period("灵活").riskLevel("LOW").build(),
                        Product.builder().code("P002").name("30 天稳健").description("短期理财，年化 3.2%").rate(3.20).period("30天").riskLevel("LOW").build(),
                        Product.builder().code("P003").name("90 天进阶").description("中等风险，年化 4.5%").rate(4.50).period("90天").riskLevel("MID").build(),
                        Product.builder().code("P004").name("365 天精选").description("高收益长期，年化 5.8%").rate(5.80).period("365天").riskLevel("HIGH").build()
                ));
                log.info("[Trade] seeded {} products", 4);
            }

            // 账单（演示客户 c-demo）
            if (billRepo.count() == 0) {
                String cid = "c-demo";
                Random r = new Random(42);
                for (int i = 0; i < 14; i++) {
                    billRepo.save(Bill.builder()
                            .customerId(cid)
                            .bizType(i % 3 == 0 ? "WITHDRAW" : (i % 3 == 1 ? "RECHARGE" : "TRADE"))
                            .amount(100.0 + r.nextInt(9000))
                            .bizDate(LocalDate.now().minusDays(i))
                            .status("SUCCESS")
                            .remark("演示账单 #" + i)
                            .build());
                }
                log.info("[Trade] seeded 14 bills for c-demo");
            }
        };
    }
}