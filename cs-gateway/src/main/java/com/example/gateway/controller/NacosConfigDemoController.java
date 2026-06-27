package com.example.gateway.controller;

import com.example.common.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Nacos 远程配置演示 (v2.2.49)
 *
 * <p>演示场景：在 nacos 控制台修改 cs-gateway.yaml 中的配置项，
 * 调用 POST /actuator/refresh 后，本控制器自动获取最新值。
 *
 * <p>用法：
 * <ol>
 *   <li>nacos 控制台 → 配置管理 → 新建 cs-gateway.yaml</li>
 *   <li>配置内容: cs.gateway.welcome=hello-from-nacos</li>
 *   <li>POST /actuator/refresh （或 @RefreshScope 自动刷新）</li>
 *   <li>GET /config-demo/welcome → 返回最新值</li>
 * </ol>
 */
@Slf4j
@RefreshScope
@RestController
@RequestMapping("/config-demo")
@RequiredArgsConstructor
public class NacosConfigDemoController {

    /**
     * 默认值：nacos 未配置时使用
     * nacos 控制台配 cs.gateway.welcome 后会覆盖
     */
    @Value("${cs.gateway.welcome:hello-from-local-default}")
    private String welcomeMessage;

    /**
     * 默认路由权重（nacos 可覆盖）
     */
    @Value("${cs.gateway.feature-flags.rate-limit-enabled:false}")
    private boolean rateLimitEnabled;

    /**
     * 默认黑名单 IP（nacos 可覆盖）
     */
    @Value("${cs.gateway.blacklist-ips:127.0.0.1,0:0:0:0:0:0:0:1}")
    private String blacklistIps;

    @GetMapping("/welcome")
    public ApiResponse<Map<String, Object>> welcome() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", welcomeMessage);
        data.put("source", "nacos or local default");
        data.put("timestamp", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    @GetMapping("/all")
    public ApiResponse<ConfigSnapshot> all() {
        ConfigSnapshot snap = new ConfigSnapshot();
        snap.setWelcomeMessage(welcomeMessage);
        snap.setRateLimitEnabled(rateLimitEnabled);
        snap.setBlacklistIps(blacklistIps);
        snap.setFetchTime(LocalDateTime.now().toString());
        log.info("[NacosConfigDemo] fetch config snapshot: {}", snap);
        return ApiResponse.ok(snap);
    }

    @Data
    public static class ConfigSnapshot {
        private String welcomeMessage;
        private boolean rateLimitEnabled;
        private String blacklistIps;
        private String fetchTime;
    }
}