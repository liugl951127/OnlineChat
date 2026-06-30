package com.example.common.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

/**
 * 国密 SM2 非对称加密 (GB/T 32918.1-2016)
 *
 * <p>用途:
 * <ul>
 *   <li>浏览器前端: SDK 拿公钥 wrap 一个随机 DEK (128 bit SM4 key)</li>
 *   <li>Java 后端: 用私钥 unwrap DEK</li>
 *   <li>C1C3C2 模式 (国密标准, 替代 RSA-OAEP)</li>
 * </ul>
 *
 * <p>用法:
 * <pre>{@code
 * // 加载服务端私钥 (CSR/PEM)
 * PrivateKey priv = SM2Util.loadPrivateKey("/root/.cs/cs-monitor.priv.pem");
 *
 * // 包装前端的 DEK (sm-crypto SM2 encrypt 后 base64 传过来)
 * byte[] dek = SM2Util.unwrapDek(priv, wrappedDekBase64);
 * }</pre>
 *
 * @author v2.2.97
 */
public final class SM2Util {

    public static final String ALG = "SM2";
    public static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    public static final String CURVE = "sm2p256v1";

    private static final SecureRandom RNG = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private SM2Util() {}

    /**
     * 用 SM2 公钥加密 (C1C3C2 模式 = 64 字节 ASN.1 头 + 32 字节 C1 + 32 字节 C3 + 32 字节 C2)
     *
     * @param pubKey SM2 公钥 (SubjectPublicKeyInfo, X.509 encoded)
     * @param data   明文 (典型 16 字节 SM4 DEK)
     * @return SM2 密文 ASN.1 DER (前端 sm-crypto 用 SM2.encrypt 后 base64 即可)
     */
    public static byte[] encrypt(PublicKey pubKey, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(ALG, PROVIDER);
        c.init(Cipher.ENCRYPT_MODE, pubKey, RNG);
        return c.doFinal(data);
    }

    /**
     * 用 SM2 私钥解密.
     */
    public static byte[] decrypt(PrivateKey privKey, byte[] data) throws GeneralSecurityException {
        try {
            Cipher c = Cipher.getInstance(ALG, PROVIDER);
            c.init(Cipher.DECRYPT_MODE, privKey, RNG);
            return c.doFinal(data);
        } catch (javax.crypto.BadPaddingException bpe) {
            // BC SM2 decrypt 包装在 BadPaddingException 里返回
            throw new javax.crypto.BadPaddingException("SM2 解密失败 (可能 wrappedDek 损坏或公私钥不匹配): " + bpe.getMessage());
        }
    }

    /**
     * 解包 DEK: base64 → bytes → decrypt
     */
    public static byte[] unwrapDek(PrivateKey priv, String wrappedDekBase64) throws GeneralSecurityException {
        byte[] wrapped = Base64.getDecoder().decode(wrappedDekBase64);
        return decrypt(priv, wrapped);
    }

    /**
     * v2.3.0: 从 PEM 文件读 SM2 keypair (公钥 + 私钥)
     *
     * <p>OpenSSL SM2 PEM 含 EC PRIVATE KEY (有完整 curve 参数),
     * BC PEMKeyPair 同时拿 priv + pub, 不需要派生
     */
    public static java.security.KeyPair loadKeyPair(String pemPath) throws Exception {
        try (PEMParser p = new PEMParser(new FileReader(pemPath))) {
            Object obj = null;
            for (int i = 0; i < 10; i++) {
                try { obj = p.readObject(); } catch (IOException ioe) { continue; }
                if (obj == null) break;
                if (obj instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                    org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter conv =
                        new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().setProvider(PROVIDER);
                    return conv.getKeyPair(kp);
                }
            }
            throw new IllegalArgumentException("PEM 中没有 PEMKeyPair (不是 OpenSSL SM2 格式)");
        }
    }

    /**
     * v2.3.0: 从 PrivateKey 派生 PublicKey (BC 方式)
     */
    public static java.security.interfaces.ECPublicKey derivePublicKey(java.security.PrivateKey priv) {
        try {
            if (priv instanceof org.bouncycastle.jce.provider.JCEECPrivateKey bPriv) {
                org.bouncycastle.jce.spec.ECParameterSpec params = bPriv.getParameters();
                if (params != null) {
                    org.bouncycastle.math.ec.ECPoint q = params.getG().multiply(bPriv.getS());
                    org.bouncycastle.jce.spec.ECPublicKeySpec pubSpec =
                        new org.bouncycastle.jce.spec.ECPublicKeySpec(q, params);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC", PROVIDER);
                    return (java.security.interfaces.ECPublicKey) kf.generatePublic(pubSpec);
                }
            }
        } catch (Exception e) {
            // 退到 PEMParser
        }
        return null;
    }

    /**
     * 从 PEM 文件读 SM2 私钥 (PKCS#8 格式 = -----BEGIN PRIVATE KEY-----)
     */
    public static PrivateKey loadPrivateKey(String pemPath) throws Exception {
        try (PEMParser p = new PEMParser(new FileReader(pemPath))) {
            // v2.3.1: 遍历整个 PEM, 收集所有可识别的对象
            //   支持: PKCS#8 PrivateKeyInfo / OpenSSL PEMKeyPair / KeyPair / 私钥参数 Object
            List<Object> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Object obj;
                try { obj = p.readObject(); } catch (IOException ioe) { continue; }
                if (obj == null) break;
                all.add(obj);
            }

            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider(PROVIDER);

            // 优先 PEMKeyPair (OpenSSL EC PRIVATE KEY + 可选 SM2 PARAMETERS)
            for (Object o : all) {
                if (o instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                    return conv.getKeyPair(kp).getPrivate();
                }
                if (o instanceof KeyPair kp) {
                    return kp.getPrivate();
                }
            }

            // 退到 PKCS#8 PrivateKeyInfo (标准 Java PKCS#8)
            for (Object o : all) {
                if (o instanceof PrivateKeyInfo pki) {
                    return conv.getPrivateKey(pki);
                }
            }

            if (all.isEmpty()) {
                throw new IllegalArgumentException("PEM 文件为空或格式不被识别");
            }
            throw new IllegalArgumentException("PEM 文件中没有可识别的私钥 (找到 " + all.size()
                + " 个段: " + all.get(0).getClass().getSimpleName() + ")");
        }
    }

    /**
     * 从 PEM 文件读 SM2 公钥 (X.509 SubjectPublicKeyInfo = -----BEGIN PUBLIC KEY-----)
     */
    public static PublicKey loadPublicKey(String pemPath) throws Exception {
        try (PEMParser p = new PEMParser(new FileReader(pemPath))) {
            List<Object> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Object obj;
                try { obj = p.readObject(); } catch (IOException ioe) { continue; }
                if (obj == null) break;
                all.add(obj);
            }

            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider(PROVIDER);

            // 优先 PEMKeyPair (含私钥 + 公钥)
            for (Object o : all) {
                if (o instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                    return conv.getKeyPair(kp).getPublic();
                }
                if (o instanceof KeyPair kp) {
                    return kp.getPublic();
                }
            }
            // 退到 X.509 SubjectPublicKeyInfo (-----BEGIN PUBLIC KEY-----)
            for (Object o : all) {
                if (o instanceof SubjectPublicKeyInfo spi) {
                    return conv.getPublicKey(spi);
                }
            }
            throw new IllegalArgumentException("公钥 PEM 无可识别段 (找到 " + all.size() + " 个)");
        }
    }

    /**
     * 公钥转 base64 (公开给前端 SDK 内嵌)
     */
    public static String toBase64(PublicKey pubKey) {
        return Base64.getEncoder().encodeToString(pubKey.getEncoded());
    }
}
