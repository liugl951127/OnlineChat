package com.example.im.service;

import com.example.common.ApiException;
import com.example.common.SecurityContextHolder;
import com.example.common.WsEnvelope;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.domain.UploadFile;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.repo.UploadFileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（图片 / 文档）
 * 存储到本地磁盘，URL 通过静态资源映射暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final UploadFileRepo fileRepo;
    private final ChatSessionRepo sessionRepo;
    private final SimpMessagingTemplate broker;

    @Value("${cs.upload.dir:./data/upload/}")
    private String storageDir;
    @Value("${cs.upload.url-prefix:/upload/}")
    private String urlPrefix;
    @Value("${cs.upload.max-size:20971520}")  // 20MB
    private long maxSize;

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> DOC_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv"
    );

    @Transactional
    public UploadFile upload(Long sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "文件为空");
        if (file.getSize() > maxSize) throw new ApiException(413, "文件过大，最大 " + (maxSize / 1024 / 1024) + "MB");

        ChatSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "会话不存在"));
        if (s.getStatus() != SessionStatus.IN_SESSION && s.getStatus() != SessionStatus.ROBOT) {
            throw new ApiException(400, "当前状态不允许上传");
        }

        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase();
        String contentType;
        if (IMAGE_TYPES.contains(mime)) contentType = "IMAGE";
        else if (DOC_TYPES.contains(mime)) contentType = "DOCUMENT";
        else throw new ApiException(415, "不支持的文件类型: " + mime);

        String role = SecurityContextHolder.current() == null ? "CUSTOMER" : SecurityContextHolder.current().getRole();
        String userId = SecurityContextHolder.requireUserId();

        // 落盘
        String ext = extOf(file.getOriginalFilename(), mime);
        String fname = "s" + sessionId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ext;
        Path dir = Paths.get(storageDir, "s" + sessionId);
        try { Files.createDirectories(dir); }
        catch (IOException e) { throw new ApiException(500, "目录创建失败"); }
        Path target = dir.resolve(fname);
        try { file.transferTo(target.toFile()); }
        catch (IOException e) {
            log.error("[Upload] save failed", e);
            throw new ApiException(500, "文件保存失败");
        }

        UploadFile uf = UploadFile.builder()
                .sessionId(sessionId)
                .customerId(s.getCustomerId())
                .uploaderId(userId)
                .uploaderRole(role)
                .contentType(contentType)
                .mimeType(mime)
                .originalName(file.getOriginalFilename() == null ? fname : file.getOriginalFilename())
                .url(urlPrefix + "s" + sessionId + "/" + fname)
                .size(file.getSize())
                .build();
        fileRepo.save(uf);

        // 通过 WS 广播给对方
        WsEnvelope env = new WsEnvelope("FILE", s.getId(),
                role.equals("AGENT") ? "AGENT" : "CUSTOMER", userId,
                contentType.equals("IMAGE") ? "[图片]" : "[文件] " + uf.getOriginalName(),
                uf, System.currentTimeMillis(), null);
        broker.convertAndSend("/topic/customer/" + s.getCustomerId(), env);
        if (s.getAgentUsername() != null) {
            broker.convertAndSend("/topic/agent/" + s.getAgentUsername(), env);
        }

        log.info("[Upload] session={} {} {} ({} bytes)", sessionId, contentType, fname, file.getSize());
        return uf;
    }

    private static String extOf(String name, String mime) {
        if (name != null && name.contains(".")) return name.substring(name.lastIndexOf('.')).toLowerCase();
        if (mime.contains("jpeg") || mime.contains("jpg")) return ".jpg";
        if (mime.contains("png")) return ".png";
        if (mime.contains("gif")) return ".gif";
        if (mime.contains("webp")) return ".webp";
        if (mime.contains("pdf")) return ".pdf";
        if (mime.contains("word")) return ".docx";
        if (mime.contains("sheet") || mime.contains("excel")) return ".xlsx";
        if (mime.contains("plain")) return ".txt";
        return "";
    }
}