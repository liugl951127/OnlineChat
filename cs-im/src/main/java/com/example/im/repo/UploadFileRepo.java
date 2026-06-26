package com.example.im.repo;

import com.example.im.domain.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadFileRepo extends JpaRepository<UploadFile, Long> {
    List<UploadFile> findBySessionIdOrderByIdAsc(Long sessionId);
}