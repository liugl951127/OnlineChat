package com.example.im.config;

import com.example.common.crypto.SM2Util;
import com.example.common.crypto.SM3Util;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * 国密 SM2 私钥加载 + 公钥暴露 (v2.2.97)
 *
 * <p>启动加载 cs.monitor.sm2-priv-pem-path, 同时缓存公钥 (供 /api/monitor/pubkey 返回给前端 SDK)
 *
 * <p>生产建议: 私钥放 HashiCorp Vault / 阿里云 KMS, 服务启动时拉取; 当前实现是本地文件.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorKeyService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${cs.monitor.sm2-priv-pem-path:/root/.cs/cs-monitor.priv.pem}")
    private String privPemPath;

    @Value("${cs.monitor.enabled:true}")
    private boolean enabled;

    private PrivateKey privateKey;
    private String publicKeyBase64;
    private String keyFingerprint;  // SM3 摘要, 用于 audit log

    public boolean isEnabled() { return enabled; }

    @PostConstruct
    public void init() throws Exception {
        if (!enabled) {
            log.info("[MonitorKey] monitor 模块未启用 (cs.monitor.enabled=false)");
            return;
        }
        try {
            // 看是 classpath 资源还是文件系统路径
            // v2.3.0: 1) 读 priv, 2) 优先 .pub.pem, 3) 退到 loadKeyPair
            String path = privPemPath;
            if (path.startsWith("classpath:")) {
                String cp = path.substring("classpath:".length());
                try (var is = new ClassPathResource(cp).getInputStream()) {
                    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("cs-monitor", ".pem");
                    java.nio.file.Files.copy(is, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    path = tmp.toString();
                }
            }
            privateKey = SM2Util.loadPrivateKey(path);
            ECPublicKey pub = null;
            // 尝试 .pub.pem 同名
            java.io.File pubFile = new java.io.File(path.replaceAll("\\.priv\\.pem$", ".pub.pem"));
            if (!pubFile.exists()) {
                pubFile = new java.io.File(path + ".pub");
            }
            if (pubFile.exists()) {
                java.security.PublicKey p = SM2Util.loadPublicKey(pubFile.toString());
                if (p instanceof ECPublicKey ep) pub = ep;
            }
            // 退到 loadKeyPair
            if (pub == null) {
                try {
                    java.security.KeyPair kp = SM2Util.loadKeyPair(path);
                    if (kp.getPublic() instanceof ECPublicKey ep) pub = ep;
                } catch (Exception ignore) {}
            }
            // 退到 derivePublicKey
            if (pub == null) {
                pub = SM2Util.derivePublicKey(privateKey);
            }
            if (pub == null) {
                throw new RuntimeException("无法获取 SM2 公钥, 请检查 .pub.pem 或用 gen-monitor-keypair.sh 生成");
            }
            publicKeyBase64 = Base64.getEncoder().encodeToString(pub.getEncoded());
            keyFingerprint = SM3Util.digestHex(pub.getEncoded()).substring(0, 16);

            log.info("[MonitorKey] ✓ SM2 私钥加载成功: path={} pub(fp)={}",
                    privPemPath, keyFingerprint);
        } catch (Exception e) {
            log.error("[MonitorKey] ✗ SM2 私钥加载失败, monitor 上传将 503: path={}", privPemPath, e);
            privateKey = null;
            publicKeyBase64 = null;
        }
    }


        public PrivateKey getPrivateKey() { return privateKey; }
    public String getPublicKeyBase64() { return publicKeyBase64; }
    public String getKeyFingerprint() { return keyFingerprint; }
}
