package com.example.im.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 上传的文件（图片 / 文档 / 语音）
 */
@Entity
@Table(name = "upload_file", indexes = {
        @Index(name = "idx_uf_session", columnList = "sessionId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UploadFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sessionId;
    @Column(length = 64)
    private String customerId;
    @Column(length = 64)
    private String uploaderId;
    @Column(nullable = false, length = 16)
    private String uploaderRole;     // CUSTOMER / AGENT
    @Column(nullable = false, length = 16)
    private String contentType;      // IMAGE / DOCUMENT / AUDIO
    @Column(length = 64)
    private String mimeType;
    @Column(nullable = false, length = 256)
    private String originalName;
    @Column(nullable = false, length = 512)
    private String url;              // 访问 URL
    @Column(nullable = false)
    private Long size;
    @Column(nullable = false)
    private Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}