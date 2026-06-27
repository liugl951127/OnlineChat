package com.example.im;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * cs-im 服务（合并：IM + Robot + Trade 金融）
 *
 * <p>模块合并后职责：
 * <ul>
 *   <li>即时通信（WebSocket / 离线消息）</li>
 *   <li>智能机器人（自动应答）</li>
 *   <li>金融产品交易（保险/理财/基金）</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan({"com.example.im", "com.example.common"})
@MapperScan("com.example.im.repo")
@EnableScheduling
public class ImApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImApplication.class, args);
    }
}