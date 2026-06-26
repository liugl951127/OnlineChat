package com.example.trade.repo;

import com.example.trade.domain.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BillRepo extends JpaRepository<Bill, Long> {
    List<Bill> findByCustomerIdAndBizDateGreaterThanEqualOrderByBizDateDesc(
            String customerId, LocalDate startDate);
}