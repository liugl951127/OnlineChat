package com.example.im.repo;

import com.example.im.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByIdDesc(Pageable p);
    Page<AuditLog> findByActionOrderByIdDesc(String action, Pageable p);
}