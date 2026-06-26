package com.example.auth.service;

import com.example.common.ApiException;
import com.example.common.CryptoUtils;
import com.example.common.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务（Mock 模式）
 */
@Slf4j
@Service
public class SmsService {

    private final StringRedisTemplate redis;
    private final RateLimiter smsSendLimiter;
    private final RateLimiter smsVerifyLimiter;

    @Value("${cs.sms.mock:true}")
    private boolean mock;
    @Value("${cs.sms.ttl-seconds:300}")
    private long codeTtl;
    @Value("${cs.sms.length:6}")
    private int codeLength;

    /** 调试模式：手机号 -> 当前验证码（mock 模式存，方便前端测试） */
    private final Map<String, String> debugCodes = new ConcurrentHashMap<>();

    public SmsService(StringRedisTemplate redis,
                      @Qualifier("smsSendLimiter") RateLimiter smsSendLimiter,
                      @Qualifier("smsVerifyLimiter") RateLimiter smsVerifyLimiter) {
        this.redis = redis;
        this.smsSendLimiter = smsSendLimiter;
        this.smsVerifyLimiter = smsVerifyLimiter;
    }

    public Map<String, Object> sendCode(String phone) {
        validatePhone(phone);
        if (!smsSendLimiter.tryAcquire("sms-send:" + phone)) {
            throw new ApiException(429, "请求过于频繁，请稍后再试");
        }

        String code = CryptoUtils.generateCode(codeLength);
        String key = "sms:code:" + phone;

        // mock 模式：内存 + redis 双重存（保证 verify 能取到）
        if (mock) {
            debugCodes.put(phone, code);
            log.info("[SMS-MOCK] phone={} code={}", phone, code);
        }
        redis.opsForValue().set(key, code, Duration.ofSeconds(codeTtl));
        redis.opsForValue().set("sms:cooldown:" + phone, "1", Duration.ofSeconds(60));

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("phone", phone);
        resp.put("ttl", codeTtl);
        if (mock) resp.put("debugCode", code);
        return resp;
    }

    public boolean verifyCode(String phone, String code) {
        validatePhone(phone);
        if (!smsVerifyLimiter.tryAcquire("sms-verify:" + phone)) {
            throw new ApiException(429, "尝试次数过多，请稍后再试");
        }
        String key = "sms:code:" + phone;
        // mock 模式优先查内存
        String expected = mock ? debugCodes.get(phone) : null;
        if (expected == null) expected = redis.opsForValue().get(key);
        if (expected == null) throw new ApiException(400, "验证码已过期，请重新获取");
        if (!constantTimeEquals(expected, code)) throw new ApiException(400, "验证码错误");
        redis.delete(key);
        debugCodes.remove(phone);
        return true;
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new ApiException(400, "手机号格式错误");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}