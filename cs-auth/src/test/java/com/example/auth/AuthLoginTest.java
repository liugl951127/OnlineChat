package com.example.auth;

import com.example.auth.domain.WechatUser;
import com.example.auth.repo.WechatUserRepo;
import com.example.auth.service.AuthService;
import com.example.auth.service.SmsService;
import com.example.common.ApiException;
import com.example.common.CryptoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户名密码登录 + 手机号登录测试
 */
@SpringBootTest
class AuthLoginTest {

    @Autowired AuthService authService;
    @Autowired SmsService smsService;
    @Autowired WechatUserRepo userRepo;

    @Test
    void cryptoUtils_hashAndVerify() {
        String pwd = "mySecret123";
        String hash = CryptoUtils.hashPassword(pwd);
        assertThat(hash).startsWith("pbkdf2$");
        assertThat(CryptoUtils.verifyPassword(pwd, hash)).isTrue();
        assertThat(CryptoUtils.verifyPassword("wrong", hash)).isFalse();
    }

    @Test
    void cryptoUtils_encryptPhone() {
        String phone = "13800138000";
        String enc = CryptoUtils.encryptPhone(phone, "test-secret-1234567890");
        assertThat(enc).isNotEqualTo(phone);
        String dec = CryptoUtils.decryptPhone(enc, "test-secret-1234567890");
        assertThat(dec).isEqualTo(phone);
    }

    @Test
    void cryptoUtils_phoneEncryption_isKeyDependent() {
        String phone = "13800138000";
        String e1 = CryptoUtils.encryptPhone(phone, "secret-1");
        String e2 = CryptoUtils.encryptPhone(phone, "secret-2");
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void register_thenLoginByPassword() {
        String username = "u" + System.nanoTime();
        Map<String, Object> reg = authService.register(username, "pass1234", null, null, "测试");
        assertThat(reg).containsKey("token");
        assertThat(reg.get("customerId")).isNotNull();

        Map<String, Object> login = authService.loginByPassword(username, "pass1234", "127.0.0.1");
        assertThat(login.get("token")).isNotNull();
        assertThat(login.get("username")).isEqualTo(username);
    }

    @Test
    void loginByPassword_wrongPwd_throwsAndIncrementFail() {
        String username = "u" + System.nanoTime();
        authService.register(username, "correctPwd", null, null, null);
        try {
            authService.loginByPassword(username, "wrongPwd", "127.0.0.1");
            org.junit.jupiter.api.Assertions.fail("应失败");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(401);
        }
        WechatUser u = userRepo.findByUsername(username).orElseThrow();
        assertThat(u.getLoginFailCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void loginByPassword_lockAfter5Fails() {
        String username = "u" + System.nanoTime();
        authService.register(username, "correctPwd", null, null, null);
        // 连续错误 5 次
        for (int i = 0; i < 5; i++) {
            try { authService.loginByPassword(username, "wrong", "127.0.0.1"); }
            catch (ApiException ignored) {}
        }
        // 第 6 次即使正确密码也被锁定
        try {
            authService.loginByPassword(username, "correctPwd", "127.0.0.1");
            org.junit.jupiter.api.Assertions.fail("应被锁定");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(423);
        }
    }

    @Test
    void register_duplicateUsername_rejected() {
        String username = "dup" + System.nanoTime();
        authService.register(username, "pass1234", null, null, null);
        try {
            authService.register(username, "pass1234", null, null, null);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(409);
        }
    }

    @Test
    void register_shortPassword_rejected() {
        try {
            authService.register("u" + System.nanoTime(), "123", null, null, null);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void sendSms_mock_returnsDebugCode() {
        Map<String, Object> r = smsService.sendCode("13800138000");
        assertThat(r).containsKey("phone");
        assertThat(r).containsKey("debugCode");
        assertThat(((String) r.get("debugCode"))).matches("\\d{6}");
    }

    @Test
    void sendSms_invalidPhone_rejected() {
        try {
            smsService.sendCode("12345");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }

    @Test
    void loginByPhone_newUser_autoRegister() {
        String phone = "139" + (System.nanoTime() % 100000000L);
        // 取验证码（mock 模式）
        String code = (String) smsService.sendCode(phone).get("debugCode");
        Map<String, Object> login = authService.loginByPhone(phone, code);
        assertThat(login).containsKey("token");
        assertThat(login.get("phoneMasked")).isNotNull();
    }

    @Test
    void loginByPhone_wrongCode_rejected() {
        try {
            smsService.sendCode("13900139000");
            authService.loginByPhone("13900139000", "000000");
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }
}