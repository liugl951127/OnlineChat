package com.example.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号消息推送：客服消息 + 模板消息。
 *
 * <p>客服消息（48 小时窗口）：客户主动发消息后，公众号可在 48 小时内主动联系。
 * 模板消息（不限时）：基于行业模板发送通知（如工单状态变更）。
 *
 * <p>沙箱 mock 模式：直接返回 mock result，不调用微信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatPushService {

    private final WechatAccessTokenCache tokenCache;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http;

    @Value("${wechat.oa.mock:true}")
    private boolean mock;

    /**
     * 发送客服消息（文本）
     *
     * @param openid 客户 openid
     * @param content 文本内容
     * @return { errcode, errmsg, msg_id }
     */
    public Map<String, Object> sendCustomerMessage(String openid, String content) {
        if (mock) {
            log.info("[WechatOA-PUSH-MOCK] customer msg → openid={} content={}", openid, content);
            Map<String, Object> r = new HashMap<>();
            r.put("errcode", 0);
            r.put("errmsg", "ok");
            r.put("msg_id", "MOCK-" + System.currentTimeMillis());
            return r;
        }
        String url = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + tokenCache.getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("msgtype", "text");
        Map<String, String> text = new HashMap<>();
        text.put("content", content);
        body.put("text", text);
        try {
            String resp = http.postForObject(url, json.writeValueAsString(body), String.class);
            log.info("[WechatOA-PUSH] customer msg → openid={} resp={}", openid, resp);
            return json.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("[WechatOA-PUSH] customer msg failed", e);
            throw new RuntimeException("send customer msg error: " + e.getMessage());
        }
    }

    /**
     * 发送模板消息
     *
     * @param openid 客户 openid
     * @param templateId 模板 ID
     * @param data 模板数据 {key: {value, color}}
     * @param url 跳转链接（可选）
     * @return { errcode, errmsg, msgid }
     */
    public Map<String, Object> sendTemplateMessage(String openid, String templateId,
                                                    Map<String, Object> data,
                                                    String url,
                                                    String miniprogramAppid,
                                                    String miniprogramPagepath) {
        if (mock) {
            log.info("[WechatOA-PUSH-MOCK] template → openid={} tpl={} data={}",
                    openid, templateId, data);
            Map<String, Object> r = new HashMap<>();
            r.put("errcode", 0);
            r.put("errmsg", "ok");
            r.put("msgid", System.currentTimeMillis());
            return r;
        }
        String apiUrl = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + tokenCache.getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);
        if (url != null) body.put("url", url);
        if (miniprogramAppid != null) {
            Map<String, String> mp = new HashMap<>();
            mp.put("appid", miniprogramAppid);
            if (miniprogramPagepath != null) mp.put("pagepath", miniprogramPagepath);
            body.put("miniprogram", mp);
        }
        try {
            String resp = http.postForObject(apiUrl, json.writeValueAsString(body), String.class);
            log.info("[WechatOA-PUSH] template → openid={} tpl={} resp={}", openid, templateId, resp);
            return json.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("[WechatOA-PUSH] template msg failed", e);
            throw new RuntimeException("send template msg error: " + e.getMessage());
        }
    }
}