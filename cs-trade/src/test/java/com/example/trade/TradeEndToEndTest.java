package com.example.trade;

import com.example.trade.domain.Bill;
import com.example.trade.repo.BillRepo;
import com.example.trade.repo.ProductRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TradeEndToEndTest {

    @Autowired BillRepo billRepo;
    @Autowired ProductRepo productRepo;

    @Test
    void seededData_exists() {
        assertThat(productRepo.count()).isGreaterThanOrEqualTo(4);
        assertThat(billRepo.count()).isGreaterThanOrEqualTo(14);
    }

    @Test
    void products_searchByKeyword() {
        List<com.example.trade.domain.Product> list = productRepo.findByNameContainingIgnoreCase("理财");
        assertThat(list).isNotEmpty();
    }
}