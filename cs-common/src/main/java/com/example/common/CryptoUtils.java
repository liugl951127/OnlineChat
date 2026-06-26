package com.example.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 加密工具：密码哈希 + 手机号 AES 加密 + 随机 token
 */
public final class CryptoUtils {
    private CryptoUtils() {}

    private static final SecureRandom RNG = new SecureRandom();

    // ==================== 密码（PBKDF2-HMAC-SHA256，无 BCrypt 依赖）====================

    /**
     * 生成密码哈希。格式：pbkdf2$<iterations>$<saltHex>$<hashHex>
     */
    public static String hashPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new ApiException(400, "密码至少 6 位");
        }
        int iterations = 100_000;
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(password.getBytes(StandardCharsets.UTF_8), salt, iterations, 32);
        return "pbkdf2$" + iterations + "$" + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(hash);
    }

    /**
     * 校验密码（恒定时间）
     */
    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null || !stored.startsWith("pbkdf2$")) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = HexFormat.of().parseHex(parts[2]);
            byte[] expected = HexFormat.of().parseHex(parts[3]);
            byte[] actual = pbkdf2(password.getBytes(StandardCharsets.UTF_8), salt, iter, expected.length);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(byte[] password, byte[] salt, int iter, int len) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    new String(password, StandardCharsets.UTF_8).toCharArray(), salt, iter, len * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }

    // ==================== 手机号 AES 加密 ====================

    private static SecretKey aesKey(String secret) throws Exception {
        // 简单派生：取 secret 的 SHA-256 前 16 字节作为 AES key
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(java.util.Arrays.copyOf(hash, 16), "AES");
    }

    public static String encryptPhone(String phone, String secret) {
        if (phone == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(secret));
            return Base64.getEncoder().encodeToString(cipher.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decryptPhone(String enc, String secret) {
        if (enc == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(secret));
            return new String(cipher.doFinal(Base64.getDecoder().decode(enc)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 验证码 ====================

    public static String generateCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }

    public static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}