package com.example.trade.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "product")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String code;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(length = 512)
    private String description;
    @Column(nullable = false)
    private Double rate;          // 年化收益率 %
    @Column(nullable = false, length = 32)
    private String period;        // 期限
    @Column(nullable = false, length = 16)
    private String riskLevel;     // LOW / MID / HIGH
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}