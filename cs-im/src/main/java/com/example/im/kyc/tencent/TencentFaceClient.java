package com.example.im.kyc.tencent;

import com.example.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 腾讯云人脸比对客户端（v2.2.0）
 *
 * <p>API：CompareFace（人脸比对 1:1）
 *
 * <p>文档：https://cloud.tencent.com/document/product/867/44987
 *
 * <p>字段：
 * <ul>
 *   <li>ImageA / ImageB — base64 或 url</li>
 *   <li>返回 Score 0-100（相似度）</li>
 *   <li>建议阈值 ≥ 80</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentFaceClient {
    private final RestTemplate http;


    private final TencentAiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 人脸比对（1:1）
     *
     * @param imageABase64 图 A base64（身份证照）
     * @param imageBBase64 图 B base64（活体照）
     * @return { score: 0-100, passed: bool }
     */
    public Map<String, Object> compareFace(String imageABase64, String imageBBase64) {
        if (isDemo(props.getSecretId()) || isDemo(props.getSecretKey())) {
            throw new ApiException(503, "腾讯云 AI 未配置：请设置 tencent.ai.secret-id/secret-key 或将 kyc.mock.face-match 改为 true");
        }

        long ts = Instant.now().getEpochSecond();
        String payload = String.format("{\"ImageA\":\"%s\",\"ImageB\":\"%s\",\"FaceModelVersion\":\"3.0\"}",
            imageABase64, imageBBase64);

        TencentCloudSigner signer = new TencentCloudSigner(
            props.getSecretId(),
            props.getSecretKey(),
            "iai",
            props.getEndpoint(),
            props.getRegion(),
            "CompareFace",
            payload,
            ts);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
        h.set("Host", props.getEndpoint());
        h.set("X-TC-Action", "CompareFace");
        h.set("X-TC-Version", "2020-03-03");
        h.set("X-TC-Timestamp", String.valueOf(ts));
        h.set("Authorization", signer.authorization());

        try {
            RestTemplate rest = http;
            ResponseEntity<String> resp = rest.exchange(
                "https://" + props.getEndpoint(),
                HttpMethod.POST,
                new HttpEntity<>(payload, h),
                String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());

            JsonNode respError = root.get("Response").get("Error");
            if (respError != null) {
                String code = respError.path("Code").asText();
                String msg = respError.path("Message").asText();
                throw new ApiException(502, "腾讯云人脸比对失败：" + code + " " + msg);
            }

            double score = root.path("Response").path("Score").asDouble(0);
            Map<String, Object> result = new HashMap<>();
            result.put("score", score);
            result.put("passed", score >= props.getFaceMatchThreshold());
            result.put("threshold", props.getFaceMatchThreshold());

            log.info("[TencentFace] 比对完成 score={} passed={}", score, result.get("passed"));
            return result;
        } catch (HttpClientErrorException e) {
            throw new ApiException(502, "腾讯云 HTTP " + e.getStatusCode() + "：" + e.getResponseBodyAsString());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "腾讯云人脸比对异常：" + e.getMessage());
        }
    }

    private boolean isDemo(String s) {
        return s == null || s.isBlank() || s.startsWith("demo-");
    }
}