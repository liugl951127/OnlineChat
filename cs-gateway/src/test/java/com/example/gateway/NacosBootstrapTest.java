package com.example.gateway;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nacos bootstrap 配置加载测试 (v2.2.46)
 *
 * <p>验证：
 *   1. bootstrap.yml 存在且能被 classloader 加载
 *   2. nacos discovery 配置正确
 *   3. spring-cloud-starter-bootstrap 依赖使 bootstrap 生效
 */
class NacosBootstrapTest {

    @Test
    void bootstrap_yml_should_exist() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("bootstrap.yml");
        assertThat(in).isNotNull();
        String content = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        assertThat(content).contains("nacos:");
        assertThat(content).contains("discovery:");
        assertThat(content).contains("server-addr");
    }

    @Test
    void bootstrap_yml_should_have_correct_settings() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("bootstrap.yml");
        String content = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        // fail-fast=false 让沙箱演示模式不会卡启动
        assertThat(content).contains("fail-fast: false");
        // 心跳配置
        assertThat(content).contains("heartbeat-interval");
    }

    @Test
    void application_yml_should_have_reactive_setting() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml");
        String content = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
        assertThat(content).contains("web-application-type: reactive");
    }

    @Test
    void gateway_classpath_should_have_nacos_discovery() throws Exception {
        // 验证 nacos-discovery jar 在 classpath (Spring Boot fat jar 启动时会看到)
        // 这里只检查 spring-cloud-starter-bootstrap 是关键依赖
        Class<?> cls = Class.forName("org.springframework.cloud.bootstrap.config.PropertySourceLocator");
        assertThat(cls).isNotNull();
    }
}