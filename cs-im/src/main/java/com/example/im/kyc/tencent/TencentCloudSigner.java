package com.example.im.kyc.tencent;

import com.example.common.ApiException;
import lombok.RequiredArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * 腾讯云 API 3.0 签名（TC3-HMAC-SHA256）
 *
 * <p>文档：https://cloud.tencent.com/document/api/172/31632
 *
 * <p>签名步骤：
 * <ol>
 *   <li>拼 CanonicalRequest</li>
 *   <li>拼 StringToSign</li>
 *   <li>计算 SecretDate / SecretService / SecretSigning</li>
 *   <li>Header Authorization: TC3-HMAC-SHA256 Credential=...</li>
 * </ol>
 */
@RequiredArgsConstructor
public class TencentCloudSigner {

    private final String secretId;
    private final String secretKey;
    private final String service;     // iai / faceid / cpdp
    private final String host;        // iai.tencentcloudapi.com
    private final String region;      // ap-guangzhou
    private final String action;      // CompareFace / DetectFace / ...
    private final String payload;     // JSON body
    private final long timestamp;     // Unix 秒

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public String authorization() {
        try {
            // 1. 拼接 CanonicalRequest
            String httpRequestMethod = "POST";
            String canonicalUri = "/";
            String canonicalQueryString = "";
            String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n" + "x-tc-action:" + action.toLowerCase() + "\n";
            String signedHeaders = "content-type;host;x-tc-action";
            String hashedRequestPayload = sha256Hex(payload);

            String canonicalRequest = httpRequestMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedRequestPayload;

            // 2. 拼 StringToSign
            String algorithm = "TC3-HMAC-SHA256";
            String credentialScope = ISO.format(Instant.ofEpochSecond(timestamp)) + "/" + service + "/tc3_request";
            String stringToSign = algorithm + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

            // 3. 计算签名
            byte[] secretDate = hmacSha256("TC3" + secretKey, ISO.format(Instant.ofEpochSecond(timestamp)));
            byte[] secretService = hmacSha256Bytes(secretDate, service);
            byte[] secretSigning = hmacSha256Bytes(secretService, "tc3_request");
            String signature = bytesToHex(hmacSha256Bytes(secretSigning, stringToSign));

            return algorithm + " "
                + "Credential=" + secretId + "/" + credentialScope + ", "
                + "SignedHeaders=" + signedHeaders + ", "
                + "Signature=" + signature;
        } catch (Exception e) {
            throw new ApiException(500, "腾讯云签名失败：" + e.getMessage());
        }
    }

    private static byte[] hmacSha256(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /** 用 byte[] 作为 key（链式签名用） */
    private static byte[] hmacSha256Bytes(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}