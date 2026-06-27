package com.example.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务发现容错测试 (v2.2.44)
 *
 * <p>验证 nacos 不可达时，DiscoveryFallback 仍能正确初始化。
 */
@SpringBootTest(classes = {DiscoveryFallbackConfig.class})
@TestPropertySource(properties = {
    "spring.application.name=test-app",
    "NACOS_DISCOVERY_ENABLED=false"
})
class DiscoveryFallbackConfigTest {

    @Autowired
    private DiscoveryFallbackConfig.DiscoveryFallback fallback;

    @Test
    void should_initialize_when_nacos_disabled() {
        assertThat(fallback).isNotNull();
        assertThat(fallback.getAppName()).isEqualTo("test-app");
        assertThat(fallback.isNacosEnabled()).isFalse();
        assertThat(fallback.getServices()).isEmpty();
    }

    @Test
    void should_initialize_when_nacos_enabled() {
        DiscoveryFallbackConfig.DiscoveryFallback f =
            new DiscoveryFallbackConfig.DiscoveryFallback("prod-app", true);
        assertThat(f.isNacosEnabled()).isTrue();
        assertThat(f.getAppName()).isEqualTo("prod-app");
    }
}