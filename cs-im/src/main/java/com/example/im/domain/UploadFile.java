package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upload_file")
public class UploadFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileId;
    private String uploaderId;
    private String sessionId;
    private String originalName;
    private String storagePath;
    private String storageUrl;
    private String mimeType;
    private Long fileSize;
    private String fileHash;
    private String scanStatus;
    private LocalDateTime scanTime;
    private Integer deleted;
    private LocalDateTime createdAt;
}