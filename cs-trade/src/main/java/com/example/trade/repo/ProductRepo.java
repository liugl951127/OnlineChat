package com.example.trade.repo;

import com.example.trade.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String keyword);
}