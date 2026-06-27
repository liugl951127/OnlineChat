package com.example.gateway.controller;

import com.example.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos 注册诊断端点 (v2.2.46)
 *
 * <p>访问 GET /actuator/nacos-diagnostics 获取详细诊断信息：
 *   - 当前服务名
 *   - nacos enabled 状态
 *   - nacos 已注册服务列表
 *   - 本服务是否注册
 *   - 本服务所有实例
 *
 * <p>用于 cs-gateway 不能自动注册时人工排查
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.cloud.client.discovery.DiscoveryClient")
public class NacosDiagnosticsController {

    private final DiscoveryClient discoveryClient;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${NACOS_DISCOVERY_ENABLED:false}")
    private boolean nacosEnabled;

    @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
    private String nacosAddr;

    @GetMapping("/nacos-diagnostics")
    public ApiResponse<Map<String, Object>> diagnostics() {
        Map<String, Object> result = new HashMap<>();
        result.put("appName", appName);
        result.put("nacosEnabled", nacosEnabled);
        result.put("nacosAddr", nacosAddr);
        result.put("port", System.getProperty("server.port", "9000"));

        if (!nacosEnabled) {
            result.put("status", "DISABLED");
            result.put("hint", "Set NACOS_DISCOVERY_ENABLED=true to enable");
            return ApiResponse.ok(result);
        }

        try {
            // 查询 nacos 上所有服务
            List<String> services = discoveryClient.getServices();
            result.put("allServices", services);
            result.put("totalServices", services.size());

            // 检查本服务是否注册
            boolean registered = services.contains(appName);
            result.put("selfRegistered", registered);

            // 查本服务的实例
            try {
                List<ServiceInstance> instances = discoveryClient.getInstances(appName);
                List<Map<String, Object>> instList = new ArrayList<>();
                for (ServiceInstance inst : instances) {
                    Map<String, Object> instMap = new HashMap<>();
                    instMap.put("instanceId", inst.getInstanceId());
                    instMap.put("host", inst.getHost());
                    instMap.put("port", inst.getPort());
                    instMap.put("uri", inst.getUri().toString());
                    instMap.put("metadata", inst.getMetadata());
                    instList.add(instMap);
                }
                result.put("selfInstances", instList);
                result.put("selfInstanceCount", instances.size());
            } catch (Exception e) {
                result.put("selfInstancesError", e.getMessage());
            }

            result.put("status", registered ? "OK" : "NOT_REGISTERED");
        } catch (Exception e) {
            log.error("[Diagnostics] failed", e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }

        return ApiResponse.ok(result);
    }
}