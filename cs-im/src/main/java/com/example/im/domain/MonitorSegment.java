package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MonitorSegment 国密加密录像分片 (v2.2.97)
 *
 * <p>前端 SDK 每 N 秒 (默认 5s) 录制一段, SM4-GCM 加密后上传,
 * 后端用 SM2 私钥 unwrap DEK, 再 SM4-GCM 解密验证 tag.
 *
 * <p>完整链路: SDK (sm-crypto SM4-GCM) → upload → cs-im 后端 (BouncyCastle 解密) → 落本地 plaintext
 */
@Data
@TableName("monitor_segment")
public class MonitorSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String customerId;
    private Integer segmentIdx;     // 序号
    private LocalDateTime startedAt;
    private Integer durationMs;

    private String ivB64;           // SM4-GCM IV (12B) base64
    private String wrappedDekB64;   // SM2 公钥 wrap 后的 16B DEK (base64)
    private byte[] ciphertextBlob;  // SM4-GCM ciphertext + 16B tag (LONGBLOB)
    private String sm3Hash;         // 256-bit SM3 hex (上传时算, 可选校验)
    private String prevSm3Hash;     // v2.3.0: 防篡改 hash 链 - 上一段的 sm3_hash

    private Long sizeBytes;
    private String storagePath;     // 解密后的本地路径
    private String kmsKeyId;        // 后端 private key 指纹

    private LocalDateTime uploadTs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
