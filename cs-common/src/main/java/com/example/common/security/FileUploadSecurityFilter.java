package com.example.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 文件上传安全过滤
 *
 * <ul>
 *   <li>检查 Content-Type 白名单</li>
 *   <li>检查扩展名白名单（防止 .jsp / .php / .exe 上传）</li>
 *   <li>检查文件大小（5MB）</li>
 *   <li>检查 Content-Disposition 中的 filename（防路径穿越）</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class FileUploadSecurityFilter implements Filter {

    private static final Set<String> ALLOWED_MIME = Set.of(
        "image/png", "image/jpeg", "image/gif", "image/webp",
        "application/pdf", "text/plain"
    );
    private static final Set<String> ALLOWED_EXT = Set.of(
        "png", "jpg", "jpeg", "gif", "webp", "pdf", "txt"
    );
    private static final Set<String> DANGEROUS_EXT = Set.of(
        "jsp", "jspx", "php", "asp", "aspx", "exe", "sh", "bat",
        "cmd", "com", "vbs", "jar", "war", "html", "htm", "svg"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            chain.doFilter(req, resp);
            return;
        }
        try {
            for (Part part : request.getParts()) {
                String name = part.getSubmittedFileName();
                long size = part.getSize();
                String mime = part.getContentType();

                // 1. 文件名检查（防路径穿越）
                if (name != null) {
                    if (name.contains("..") || name.contains("/") || name.contains("\\")) {
                        reject(response, "文件名包含非法字符: " + name);
                        return;
                    }
                    String ext = getExt(name).toLowerCase();
                    if (DANGEROUS_EXT.contains(ext)) {
                        reject(response, "危险文件类型: ." + ext);
                        return;
                    }
                    if (!ALLOWED_EXT.contains(ext)) {
                        reject(response, "不支持的文件扩展名: ." + ext);
                        return;
                    }
                }

                // 2. MIME 检查（防止 MIME 欺骗）
                if (mime != null && !ALLOWED_MIME.contains(mime.toLowerCase())) {
                    reject(response, "不支持的 MIME 类型: " + mime);
                    return;
                }

                // 3. 大小检查
                if (size > MAX_SIZE) {
                    reject(response, "文件超过 5MB");
                    return;
                }

                // 4. 魔数检查（前 4 字节）
                if (size >= 4) {
                    try (java.io.InputStream is = part.getInputStream()) {
                        byte[] head = new byte[8];
                        int n = is.read(head);
                        if (n >= 4 && !checkMagicNumber(head, n, name)) {
                            reject(response, "文件内容与扩展名不符");
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Upload-Security] rejected: {}", e.getMessage());
            reject(response, e.getMessage());
            return;
        }
        chain.doFilter(req, resp);
    }

    private boolean checkMagicNumber(byte[] head, int n, String name) {
        String ext = getExt(name).toLowerCase();
        // PNG: 89 50 4E 47
        if ("png".equals(ext)) return head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
        // JPEG: FF D8 FF
        if ("jpg".equals(ext) || "jpeg".equals(ext)) return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        // GIF: 47 49 46 38
        if ("gif".equals(ext)) return head[0] == 0x47 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x38;
        // PDF: 25 50 44 46
        if ("pdf".equals(ext)) return head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
        // WebP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
        return true; // 其他类型不做严格检查
    }

    private String getExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private void reject(HttpServletResponse resp, String msg) throws IOException {
        log.warn("[Upload-Security] rejected: {}", msg);
        resp.setStatus(400);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":400,\"msg\":\"" + msg + "\"}");
    }
}