package com.example.common.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * 国密 SM4 分组密码 + GCM 模式 (GM/T 0044-2016)
 *
 * <p>SM4 是 128 bit 块, 128 bit 密钥的对称算法, 替代 AES.
 * GCM 模式 = 认证加密 (AEAD), 提供:
 * <ul>
 *   <li>机密性 (encryption)</li>
 *   <li>完整性 (auth tag)</li>
 *   <li>每片独立 IV (96 bit) + tag (128 bit)</li>
 * </ul>
 *
 * <p>用法:
 * <pre>{@code
 * byte[] key  = randomBytes(16);                  // DEK
 * byte[] iv   = randomBytes(12);                  // per-segment IV
 * byte[] ciphertext = SM4Util.encrypt(key, iv, data);
 * // 验证 + 解密 (unwraps authentication tag)
 * byte[] plain = SM4Util.decrypt(key, iv, ciphertext);
 * }</pre>
 *
 * <p>前端 polyfill 用 sm-crypto 库:
 * <pre>{@code
 * import { sm4 } from 'sm-crypto'
 * const enc = sm4.encrypt(data, key.padEnd(32, '0'), { mode: 'gcm', iv: ivHex })
 * }</pre>
 *
 * @author v2.2.97
 */
public final class SM4Util {

    public static final String TRANSFORMATION = "SM4/GCM/NoPadding";
    public static final int KEY_LEN_BYTES = 16;     // 128 bit
    public static final int IV_LEN_BYTES = 12;       // 96 bit (GCM 推荐)
    public static final int TAG_LEN_BITS = 128;      // 16 字节

    private static final SecureRandom RNG = new SecureRandom();

    static {
        if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
    }

    private SM4Util() {}

    /**
     * 生成随机 DEK (128 bit)
     */
    public static byte[] generateKey() {
        byte[] k = new byte[KEY_LEN_BYTES];
        RNG.nextBytes(k);
        return k;
    }

    /**
     * 生成 IV (96 bit)
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LEN_BYTES];
        RNG.nextBytes(iv);
        return iv;
    }

    /**
     * SM4-GCM 加密 (返回: ciphertext || tag, 共 plain.length + 16 字节)
     */
    public static byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) throws Exception {
        if (key.length != KEY_LEN_BYTES) throw new IllegalArgumentException("SM4 key must be 16 bytes");
        if (iv.length != IV_LEN_BYTES) throw new IllegalArgumentException("IV must be 12 bytes for GCM");
        Cipher c = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"),
                new GCMParameterSpec(TAG_LEN_BITS, iv));
        return c.doFinal(plaintext);
    }

    /**
     * SM4-GCM 解密 (输入 ciphertext || tag, throws if 完整性验证失败)
     */
    public static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertextWithTag) throws Exception {
        if (key.length != KEY_LEN_BYTES) throw new IllegalArgumentException("SM4 key must be 16 bytes");
        if (iv.length != IV_LEN_BYTES) throw new IllegalArgumentException("IV must be 12 bytes for GCM");
        if (ciphertextWithTag.length < TAG_LEN_BITS / 8)
            throw new IllegalArgumentException("ciphertext too short for GCM tag");
        Cipher c = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"),
                new GCMParameterSpec(TAG_LEN_BITS, iv));
        return c.doFinal(ciphertextWithTag);   // 抛 AEADBadTagException 如果不匹配
    }
}
