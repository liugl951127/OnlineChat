package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nacos 注册诊断 (v2.2.46)
 *
 * <p>cs-gateway 启动后立即检测：
 *   - 当前服务名
 *   - 注册到 nacos 的服务列表
 *   - 自身是否被注册
 *
 * <p>输出详细诊断日志到控制台和 logs/cs-gateway.log
 */
@Slf4j
@Component
public class NacosRegistrationConfig {

    private final DiscoveryClient discoveryClient;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${NACOS_DISCOVERY_ENABLED:false}")
    private boolean nacosEnabled;

    public NacosRegistrationConfig(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("============================================================");
        log.info("[NacosRegistration] 应用启动完成");
        log.info("[NacosRegistration] 应用名: {}", appName);
        log.info("[NacosRegistration] nacos enabled: {}", nacosEnabled);

        if (!nacosEnabled) {
            log.warn("[NacosRegistration] nacos disabled (NACOS_DISCOVERY_ENABLED=false)");
            log.info("[NacosRegistration] 设置 NACOS_DISCOVERY_ENABLED=true 启用注册发现");
            return;
        }

        try {
            List<String> services = discoveryClient.getServices();
            log.info("[NacosRegistration] nacos 已注册服务数: {}", services.size());
            for (String svc : services) {
                log.info("  ✓ {}", svc);
            }

            if (services.contains(appName)) {
                log.info("✓✓✓ [NacosRegistration] 本服务 {} 已注册到 nacos", appName);
            } else {
                log.warn("✗✗✗ [NacosRegistration] 本服务 {} 未注册到 nacos", appName);
                log.warn("[NacosRegistration] 检查项:");
                log.warn("  1. NACOS_ADDR 是否正确? 当前: {}", System.getenv("NACOS_ADDR"));
                log.warn("  2. nacos 服务是否在 {} 端口运行?", "8848");
                log.warn("  3. cs-gateway pom 是否有 nacos-discovery 依赖?");
                log.warn("  4. @EnableDiscoveryClient 注解是否在 Application 类上?");
                log.warn("  5. spring-cloud-starter-loadbalancer 依赖是否在 classpath?");
            }

            // 列出本服务的所有实例
            try {
                List<org.springframework.cloud.client.ServiceInstance> instances =
                    discoveryClient.getInstances(appName);
                log.info("[NacosRegistration] 本服务实例数: {}", instances.size());
                for (org.springframework.cloud.client.ServiceInstance inst : instances) {
                    log.info("  实例: {}:{} (instanceId={})",
                        inst.getHost(), inst.getPort(), inst.getInstanceId());
                }
            } catch (Exception e) {
                log.warn("[NacosRegistration] 查询本服务实例失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("[NacosRegistration] 查询服务列表失败: {}", e.getMessage(), e);
        }
        log.info("============================================================");
    }
}