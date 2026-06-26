package com.example.trade.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bill", indexes = {
        @Index(name = "idx_bill_customer", columnList = "customerId"),
        @Index(name = "idx_bill_date", columnList = "bizDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String customerId;
    @Column(nullable = false, length = 32)
    private String bizType;       // RECHARGE / WITHDRAW / TRADE / REFUND
    @Column(nullable = false)
    private Double amount;
    @Column(nullable = false)
    private LocalDate bizDate;
    @Column(nullable = false, length = 32)
    private String status;        // SUCCESS / FAILED
    @Column(length = 256)
    private String remark;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}