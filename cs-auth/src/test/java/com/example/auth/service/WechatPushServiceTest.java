package com.example.auth.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.2.28 微信推送接口单元测试（mock 模式）。
 *
 * <p>不需要外部依赖，验证：
 * <ul>
 *   <li>JS-SDK 签名算法（SHA1, noncestr/timestamp 长度）</li>
 *   <li>客服消息 mock 返回</li>
 *   <li>模板消息 mock 返回</li>
 *   <li>小程序订阅消息 mock 返回</li>
 * </ul>
 */
class WechatPushServiceTest {

    @Test
    void jsSign_mock_returns_4_fields() {
        // 用真实 cache 跑（mock=true）
        org.springframework.data.redis.core.StringRedisTemplate redis =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        WechatAccessTokenCache cache = new WechatAccessTokenCache(redis);
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "mock", true);
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "appId", "wx-test");

        WechatJsSignService svc = new WechatJsSignService(cache);
        Map<String, String> r = svc.sign("https://example.com/page");

        assertEquals("wx-test", r.get("appId"));
        assertNotNull(r.get("timestamp"));
        assertNotNull(r.get("nonceStr"));
        assertNotNull(r.get("signature"));
        assertEquals(40, r.get("signature").length(), "sha1 = 40 hex chars");
        assertTrue(Long.parseLong(r.get("timestamp")) > 0);
    }

    @Test
    void customerMessage_mock_returns_ok() {
        org.springframework.data.redis.core.StringRedisTemplate redis =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        WechatAccessTokenCache cache = new WechatAccessTokenCache(redis);
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "mock", true);

        WechatPushService svc = new WechatPushService(cache);
        Map<String, Object> r = svc.sendCustomerMessage("oa-mock-test", "hello");
        assertEquals(0, r.get("errcode"));
        assertEquals("ok", r.get("errmsg"));
        assertTrue(r.get("msg_id").toString().startsWith("MOCK-"));
    }

    @Test
    void templateMessage_mock_returns_ok() {
        org.springframework.data.redis.core.StringRedisTemplate redis =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        WechatAccessTokenCache cache = new WechatAccessTokenCache(redis);
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "mock", true);

        WechatPushService svc = new WechatPushService(cache);
        Map<String, Object> data = new HashMap<>();
        Map<String, String> first = new HashMap<>();
        first.put("value", "工单已处理");
        data.put("first", first);
        Map<String, Object> r = svc.sendTemplateMessage("oa-mock", "tpl-id", data, null, null, null);
        assertEquals(0, r.get("errcode"));
    }

    @Test
    void miniSubscribe_mock_returns_ok() {
        org.springframework.data.redis.core.StringRedisTemplate redis =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        WxMiniSubscribeService svc = new WxMiniSubscribeService(redis);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "mock", true);

        Map<String, Object> data = new HashMap<>();
        Map<String, String> thing = new HashMap<>();
        thing.put("value", "您有新消息");
        data.put("thing1", thing);
        Map<String, Object> r = svc.send("mini-openid", "mini-tpl-id", data, "pages/index/index");
        assertEquals(0, r.get("errcode"));
    }
}