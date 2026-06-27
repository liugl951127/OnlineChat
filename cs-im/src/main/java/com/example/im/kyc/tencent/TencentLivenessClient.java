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
import java.util.List;
import java.util.Map;

/**
 * 腾讯云慧眼活体检测客户端（v2.2.0）
 *
 * <p>API：faceid.tencentcloudapi.com - LivenessRecognition
 *
 * <p>文档：https://cloud.tencent.com/document/product/1007/56811
 *
 * <p>支持模式：
 * <ul>
 *   <li>SILENT — 静默活体（仅图片）</li>
 *   <li>ACTION — 动作活体（客户按提示完成张嘴/眨眼/摇头）</li>
 *   <li>VIDEO — 视频活体（上传一段视频）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentLivenessClient {

    private final TencentAiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 动作活体检测（按动作序列）
     *
     * @param videoBase64 客户录制的视频 base64（webm/mp4）
     * @param actions     动作序列 [MOUTH/BLINK/SHAKE/NOD]
     * @return { passed, score, detail }
     */
    public Map<String, Object> checkActionLiveness(String videoBase64, List<String> actions) {
        if (isDemo(props.getSecretId()) || isDemo(props.getSecretKey())) {
            throw new ApiException(503, "腾讯云未配置：请设置 tencent.ai.secret-id/secret-key 或将 kyc.mock.liveness 改为 true");
        }

        long ts = Instant.now().getEpochSecond();

        // LivenessRecognition 需要先用 CreateFaceIdToken 拿到 BizToken，再用 VideoLivenessCompare
        // 这里简化：直接调用 LivenessRecognition（输入图片 base64）
        // 真实场景通常用分两步：1) CreateFaceIdToken 2) GetFaceIdResult 或 DetectFaceId

        String actionsStr = String.join(",", actions);
        String payload = String.format(
            "{\"VideoBase64\":\"%s\",\"ActionList\":\"%s\",\"LivenessType\":\"ACTION\"}",
            videoBase64, actionsStr);

        TencentCloudSigner signer = new TencentCloudSigner(
            props.getSecretId(),
            props.getSecretKey(),
            "faceid",
            "faceid.tencentcloudapi.com",
            props.getRegion(),
            "LivenessRecognition",
            payload,
            ts);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
        h.set("Host", "faceid.tencentcloudapi.com");
        h.set("X-TC-Action", "LivenessRecognition");
        h.set("X-TC-Version", "2018-03-01");
        h.set("X-TC-Timestamp", String.valueOf(ts));
        h.set("Authorization", signer.authorization());

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.exchange(
                "https://faceid.tencentcloudapi.com",
                HttpMethod.POST,
                new HttpEntity<>(payload, h),
                String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode respNode = root.path("Response");
            JsonNode respError = respNode.path("Error");
            if (!respError.isMissingNode()) {
                String code = respError.path("Code").asText();
                String msg = respError.path("Message").asText();
                throw new ApiException(502, "腾讯云活体检测失败：" + code + " " + msg);
            }

            double score = respNode.path("Score").asDouble(0);
            boolean passed = respNode.path("Result").asText("FAIL").equals("PASS");

            Map<String, Object> result = new HashMap<>();
            result.put("passed", passed);
            result.put("score", Math.round(score * 100) / 100.0);
            result.put("detail", "腾讯云慧眼 " + (passed ? "通过" : "拒绝"));
            result.put("actions", actions);
            return result;
        } catch (HttpClientErrorException e) {
            throw new ApiException(502, "腾讯云 HTTP " + e.getStatusCode() + "：" + e.getResponseBodyAsString());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "腾讯云活体检测异常：" + e.getMessage());
        }
    }

    /** 静默活体（仅图片，无需动作） */
    public Map<String, Object> checkSilentLiveness(String imageBase64) {
        if (isDemo(props.getSecretId()) || isDemo(props.getSecretKey())) {
            throw new ApiException(503, "腾讯云未配置");
        }

        long ts = Instant.now().getEpochSecond();
        String payload = String.format("{\"ImageBase64\":\"%s\"}", imageBase64);

        TencentCloudSigner signer = new TencentCloudSigner(
            props.getSecretId(),
            props.getSecretKey(),
            "faceid",
            "faceid.tencentcloudapi.com",
            props.getRegion(),
            "ImageRecognition",
            payload,
            ts);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
        h.set("Host", "faceid.tencentcloudapi.com");
        h.set("X-TC-Action", "ImageRecognition");
        h.set("X-TC-Version", "2018-03-01");
        h.set("X-TC-Timestamp", String.valueOf(ts));
        h.set("Authorization", signer.authorization());

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.exchange(
                "https://faceid.tencentcloudapi.com",
                HttpMethod.POST,
                new HttpEntity<>(payload, h),
                String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode respNode = root.path("Response");
            JsonNode respError = respNode.path("Error");
            if (!respError.isMissingNode()) {
                throw new ApiException(502, "腾讯云：" + respError.path("Code").asText() + " " + respError.path("Message").asText());
            }

            double score = respNode.path("Score").asDouble(0);
            boolean passed = respNode.path("Result").asText("FAIL").equals("PASS");

            Map<String, Object> result = new HashMap<>();
            result.put("passed", passed);
            result.put("score", Math.round(score * 100) / 100.0);
            result.put("detail", "静默活体 " + (passed ? "通过" : "拒绝"));
            return result;
        } catch (HttpClientErrorException e) {
            throw new ApiException(502, "腾讯云 HTTP " + e.getStatusCode() + "：" + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ApiException(500, "腾讯云静默活体异常：" + e.getMessage());
        }
    }

    private boolean isDemo(String s) {
        return s == null || s.isBlank() || s.startsWith("demo-");
    }
}