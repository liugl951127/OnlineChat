package com.example.im.kyc.baidu;

import com.example.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 百度 OCR REST 客户端（v2.1.1）
 *
 * <p>零 SDK 依赖，直接调百度智能云 REST API。
 *
 * <p>API 文档：
 * <ul>
 *   <li>身份证识别：https://cloud.baidu.com/doc/OCR/s/rk3h7xzck</li>
 *   <li>access_token：https://cloud.baidu.com/doc/REFERENCE/s/Ck3dirh8v</li>
 * </ul>
 *
 * <p>access_token 缓存：有效期 30 天，按 appId+apiKey+secretKey 缓存到内存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiduOcrClient {
    private final RestTemplate http;


    private final BaiduOcrProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** access_token 缓存 key → { token, expiresAt } */
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 识别身份证
     *
     * @param frontImageBase64 正面图 base64（不含 data:image/png;base64, 前缀）
     * @param backImageBase64  反面图 base64
     * @return 识别结果
     *         <ul>
     *           <li>name — 姓名</li>
     *           <li>gender — 性别（M/F）</li>
     *           <li>nation — 民族</li>
     *           <li>birth — 出生日期 YYYYMMDD</li>
     *           <li>address — 地址</li>
     *           <li>idCardNo — 身份证号</li>
     *           <li>issueAuthority — 签发机关（背面）</li>
     *           <li>validPeriod — 有效期（背面）</li>
     *         </ul>
     */
    public Map<String, Object> recognizeIdCard(String frontImageBase64, String backImageBase64) {
        if (isBlank(props.getAppId()) || isBlank(props.getApiKey()) || isBlank(props.getSecretKey())
            || props.getAppId().startsWith("demo-")) {
            throw new ApiException(503, "百度 OCR 未配置：请设置 baidu.ocr.app-id/api-key/secret-key 或将 kyc.mock.ocr 改为 true");
        }

        log.info("[BaiduOCR] 开始身份证识别 frontSize={} backSize={}",
            frontImageBase64 == null ? 0 : frontImageBase64.length(),
            backImageBase64 == null ? 0 : backImageBase64.length());

        // 1) 调正面（姓名/性别/民族/出生/地址/身份证号）
        Map<String, Object> front = callIdCard(frontImageBase64, "front");
        // 2) 调反面（签发机关/有效期）
        Map<String, Object> back = backImageBase64 == null
            ? Collections.emptyMap()
            : callIdCard(backImageBase64, "back");

        // 3) 合并
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", front.get("姓名"));
        result.put("gender", mapGender(front.get("性别")));
        result.put("nation", front.get("民族"));
        result.put("birth", normalizeDate(front.get("出生")));
        result.put("address", front.get("住址"));
        result.put("idCardNo", front.get("公民身份号码"));
        result.put("issueAuthority", back.get("签发机关"));
        result.put("validPeriod", back.get("有效期限"));

        // 4) 校验：身份证号必填
        if (isBlank((String) result.get("idCardNo"))) {
            throw new ApiException(400, "OCR 未识别到身份证号，请检查图片清晰度");
        }

        log.info("[BaiduOCR] 识别成功 name={} idCard={}",
            result.get("name"), maskIdCard((String) result.get("idCardNo")));
        return result;
    }

    /** 调百度身份证识别接口 */
    private Map<String, Object> callIdCard(String imageBase64, String side) {
        String accessToken = getAccessToken();
        String url = props.getEndpoint() + "/idcard?access_token=" + accessToken;

        // form 表单
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("image", URLEncoder.encode(imageBase64, StandardCharsets.UTF_8));
        form.add("id_card_side", side);  // front / back
        form.add("detect_direction", "false");
        form.add("detect_photo", "false");
        form.add("detect_risk", "false");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            RestTemplate rest = http;
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST,
                new HttpEntity<>(form, h), String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            // 错误码
            JsonNode errorCode = root.get("error_code");
            if (errorCode != null && errorCode.asInt() != 0) {
                String errMsg = root.path("error_msg").asText("unknown");
                throw new ApiException(502, "百度 OCR 调用失败：" + errorCode.asInt() + " " + errMsg);
            }
            // 解析 words_result
            JsonNode wordsResult = root.path("words_result");
            if (wordsResult.isObject() && wordsResult.size() > 0) {
                Map<String, Object> map = new HashMap<>();
                wordsResult.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().path("words").asText()));
                return map;
            }
            throw new ApiException(502, "百度 OCR 返回空结果");
        } catch (HttpClientErrorException e) {
            throw new ApiException(502, "百度 OCR HTTP 错误：" + e.getStatusCode() + " " + e.getResponseBodyAsString());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "百度 OCR 调用异常：" + e.getMessage());
        }
    }

    /** 获取 access_token（缓存 30 天） */
    private String getAccessToken() {
        String cacheKey = props.getApiKey() + ":" + props.getSecretKey();
        TokenCache cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis() + 60_000) {
            return cached.token;
        }
        lock.lock();
        try {
            // 双重检查
            cached = tokenCache.get(cacheKey);
            if (cached != null && cached.expiresAt > System.currentTimeMillis() + 60_000) {
                return cached.token;
            }
            String url = "https://aip.baidubce.com/oauth/2.0/token"
                + "?grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(props.getApiKey(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(props.getSecretKey(), StandardCharsets.UTF_8);

            RestTemplate rest = http;
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());

            String token = root.path("access_token").asText(null);
            long expiresIn = root.path("expires_in").asLong(2592000L); // 默认 30 天

            if (token == null || token.isBlank()) {
                String err = root.path("error_description").asText("unknown");
                throw new ApiException(502, "百度 access_token 获取失败：" + err);
            }

            tokenCache.put(cacheKey, new TokenCache(token,
                System.currentTimeMillis() + expiresIn * 1000L));
            log.info("[BaiduOCR] 获取 access_token 成功 expiresIn={}s", expiresIn);
            return token;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "百度 access_token 调用异常：" + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** 男 → M，女 → F */
    private String mapGender(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.contains("男")) return "M";
        if (s.contains("女")) return "F";
        return s;
    }

    /** 19900115 → 1990-01-15 */
    private String normalizeDate(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().replaceAll("\\D", "");
        if (s.length() != 8) return raw.toString();
        return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8);
    }

    /** 身份证脱敏 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return "****";
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record TokenCache(String token, long expiresAt) {}
}