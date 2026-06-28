package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态服务发现 fallback (v2.2.85)
 *
 * <p>沙箱/演示模式没有 nacos 时, Spring Cloud LoadBalancer 没法解析 {@code lb://cs-auth} 等 URI.
 * 本类提供 {@link ReactiveDiscoveryClient} 静态实现, 让 lb:// 直接解析为 IP:port.
 *
 * <p>配置 (application.yml):
 * <pre>
 * cs:
 *   static-discovery:
 *     enabled: ${STATIC_DISCOVERY_ENABLED:false}
 *     services:
 *       cs-auth:     127.0.0.1:9001
 *       cs-im:       127.0.0.1:9003
 *       cs-message:  127.0.0.1:9005
 *       cs-robot:    127.0.0.1:9006
 * </pre>
 *
 * <p>当 {@code STATIC_DISCOVERY_ENABLED=true} 时启用, 4 服务通过 lb:// 路由到指定 IP.
 */
@Slf4j
@Configuration
public class StaticServiceDiscoveryConfig {

    @Value("${cs.static-discovery.enabled:false}")
    private boolean enabled;

    @Value("${cs.static-discovery.services.cs-auth:127.0.0.1:9001}")
    private String authAddr;

    @Value("${cs.static-discovery.services.cs-im:127.0.0.1:9003}")
    private String imAddr;

    @Value("${cs.static-discovery.services.cs-message:127.0.0.1:9005}")
    private String messageAddr;

    @Value("${cs.static-discovery.services.cs-robot:127.0.0.1:9005}")
    private String robotAddr;

    /**
     * 静态 DiscoveryClient bean (给 WebClient 调用使用)
     */
    @Bean
    public DiscoveryClient staticDiscoveryClient() {
        if (!enabled) {
            log.info("[StaticDiscovery] 未启用 (STATIC_DISCOVERY_ENABLED=false), 用 nacos");
            return new EmptyDiscoveryClient();
        }
        Map<String, ServiceInstance> map = new ConcurrentHashMap<>();
        map.put("cs-auth",    parseInstance("cs-auth", authAddr));
        map.put("cs-im",      parseInstance("cs-im", imAddr));
        map.put("cs-message", parseInstance("cs-message", messageAddr));
        map.put("cs-robot",   parseInstance("cs-robot", robotAddr));
        log.info("[StaticDiscovery] 启用, 服务清单:");
        map.forEach((k, v) -> log.info("  - {} → {}:{}", k, v.getHost(), v.getPort()));
        return new StaticDiscoveryClient(map);
    }

    /**
     * 静态 ReactiveDiscoveryClient bean (WebFlux Gateway 用)
     */
    @Bean
    public ReactiveDiscoveryClient staticReactiveDiscoveryClient(DiscoveryClient client) {
        return new StaticReactiveDiscoveryClient(client);
    }

    private ServiceInstance parseInstance(String serviceId, String addr) {
        String[] parts = addr.split(":");
        return new DefaultServiceInstance(
                serviceId + "-1",
                serviceId,
                parts[0],
                Integer.parseInt(parts[1]),
                false);
    }

    /**
     * 静态 DiscoveryClient - 返回固定服务列表
     */
    static class StaticDiscoveryClient implements DiscoveryClient {
        private final Map<String, ServiceInstance> instances;

        StaticDiscoveryClient(Map<String, ServiceInstance> instances) {
            this.instances = instances;
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            ServiceInstance inst = instances.get(serviceId);
            return inst == null ? Collections.emptyList() : Collections.singletonList(inst);
        }

        @Override
        public List<String> getServices() {
            return new ArrayList<>(instances.keySet());
        }

        @Override
        public String description() {
            return "StaticDiscoveryClient (v2.2.85 fallback mode)";
        }
    }

    /**
     * 空 DiscoveryClient (未启用静态模式时)
     */
    static class EmptyDiscoveryClient implements DiscoveryClient {
        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return Collections.emptyList();
        }
        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }

        @Override
        public String description() {
            return "EmptyDiscoveryClient";
        }
    }

    /**
     * Reactive 包装, 让 LoadBalancer 能用
     */
    static class StaticReactiveDiscoveryClient implements ReactiveDiscoveryClient {
        private final DiscoveryClient delegate;

        StaticReactiveDiscoveryClient(DiscoveryClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Flux<ServiceInstance> getInstances(String serviceId) {
            return Flux.fromIterable(delegate.getInstances(serviceId));
        }

        @Override
        public Flux<String> getServices() {
            return Flux.fromIterable(delegate.getServices());
        }

        @Override
        public String description() {
            return "StaticReactiveDiscoveryClient";
        }
    }
}