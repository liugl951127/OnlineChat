package com.example.common.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 国密 SM3 哈希 (GB/T 32905-2016)
 *
 * <p>替代 SHA-256, 256 bit 摘要. 用于:
 * <ul>
 *   <li>校验 segment 完整性 (配合 GCM tag)</li>
 *   <li>manifest hash 链 (防篡改)</li>
 *   <li>SM4 DEK fingerprint</li>
 * </ul>
 *
 * @author v2.2.97
 */
public final class SM3Util {

    static {
        if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
    }

    private SM3Util() {}

    public static byte[] digest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME);
            return md.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 不可用", e);
        }
    }

    public static String digestHex(byte[] data) {
        return toHex(digest(data));
    }

    public static String digestHex(String s) {
        return digestHex(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 16 进制
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
