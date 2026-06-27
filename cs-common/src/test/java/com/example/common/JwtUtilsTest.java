package com.example.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtils v2.2.35 单元测试：
 * - secret 长度校验
 * - blacklist checker
 * - token issue / parse
 */
class JwtUtilsTest {

    @Test
    void short_secret_throws_exception() {
        // < 32 字节 secret 应该抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtils("short", 86400000));
    }

    @Test
    void long_secret_works() {
        // >= 32 字节 secret OK
        JwtUtils utils = new JwtUtils("this-is-a-32-byte-or-longer-secret-1234", 86400000);
        assertNotNull(utils);
    }

    @Test
    void issue_and_parse_roundtrip() {
        JwtUtils utils = new JwtUtils("this-is-a-32-byte-or-longer-secret-1234", 86400000);
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CUSTOMER");
        claims.put("userId", "c-test");
        String token = utils.issue("c-test", claims);
        assertNotNull(token);
        assertTrue(token.startsWith("eyJ"));

        var parsed = utils.parse(token);
        assertNotNull(parsed);
        assertEquals("c-test", parsed.getSubject());
        assertEquals("CUSTOMER", parsed.get("role"));
    }

    @Test
    void parse_and_check_blacklist_blocks_revoked_token() {
        JwtUtils utils = new JwtUtils("this-is-a-32-byte-or-longer-secret-1234", 86400000);

        // 装一个简单的 blacklist checker
        java.util.Set<String> blacklist = new java.util.HashSet<>();
        Predicate<String> checker = blacklist::contains;
        utils.setTokenBlacklistChecker(checker);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CUSTOMER");
        String token = utils.issue("c-test", claims);

        // 未拉黑 → 返回 claims
        var parsed = utils.parseAndCheck(token);
        assertNotNull(parsed);
        assertEquals("c-test", parsed.getSubject());

        // 拉黑 → 返回 null
        blacklist.add(token);
        assertNull(utils.parseAndCheck(token));
    }

    @Test
    void tampered_token_parse_returns_null() {
        JwtUtils utils = new JwtUtils("this-is-a-32-byte-or-longer-secret-1234", 86400000);
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CUSTOMER");
        String token = utils.issue("c-test", claims);

        // 篡改 token（改最后一字符）
        String tampered = token.substring(0, token.length() - 2) + "XX";
        assertNull(utils.parse(tampered));
    }
}