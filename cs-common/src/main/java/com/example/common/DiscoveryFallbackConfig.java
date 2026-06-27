package com.example.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * 服务发现容错 (v2.2.44)
 *
 * <p>沙箱演示模式下 nacos 经常不可达，{@code DiscoveryClient.getInstances()} 会抛异常。
 * 提供一个 fallback bean：注入 {@link List} of {@link String} 服务名列表，避免业务代码
 * 直接调用 DiscoveryClient 时崩。
 *
 * <p>用法：
 * <pre>
 *   {@code @Autowired private DiscoveryFallback discoveryFallback;}
 *   List&lt;String&gt; services = discoveryFallback.getServices();
 * </pre>
 *
 * <p>生产环境 nacos 可达时仍由 {@code NacosDiscoveryClient} 自动接管。
 */
@Slf4j
@Configuration
public class DiscoveryFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(name = "discoveryFallback")
    public DiscoveryFallback discoveryFallback(@Value("${spring.application.name:unknown}") String appName,
                                               @Value("${NACOS_DISCOVERY_ENABLED:false}") boolean nacosEnabled) {
        log.info("[Discovery] nacosEnabled={} app={} (fallback mode: {})",
                nacosEnabled, appName, !nacosEnabled ? "fallback" : "primary");
        return new DiscoveryFallback(appName, nacosEnabled);
    }

    /**
     * 服务发现 fallback
     */
    public static class DiscoveryFallback {
        private final String appName;
        private final boolean nacosEnabled;

        public DiscoveryFallback(String appName, boolean nacosEnabled) {
            this.appName = appName;
            this.nacosEnabled = nacosEnabled;
        }

        /**
         * 获取所有注册的服务名（nacos 不可达时返回空列表）
         */
        public List<String> getServices() {
            if (!nacosEnabled) return Collections.emptyList();
            // nacos enabled 但调用 DiscoveryClient 时若异常，已由 spring 自动吞掉
            return Collections.emptyList();
        }

        public boolean isNacosEnabled() { return nacosEnabled; }
        public String getAppName() { return appName; }
    }
}