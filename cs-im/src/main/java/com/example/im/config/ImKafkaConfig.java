package com.example.im.config;

import com.example.common.kafka.ChatMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * IM 服务的 Kafka 消费者配置
 */
@Slf4j
@EnableKafka
@Configuration
public class ImKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, ChatMessageEvent> chatMessageConsumerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, "cs-im-group");
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.common.kafka");
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());

        // v2.2.91: 调优消费者参数 (避免 rebalance + 处理超时)
        // 默认 session.timeout.ms=45s 在网络抖动时会频繁 rebalance
        // 默认 max.poll.interval.ms=5min 在批处理消息时会超时
        cfg.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);        // 30s (默认 45s)
        cfg.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);    // 10s (默认 3s, 1/3 session)
        cfg.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);    // 10min (默认 5min)
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);          // 100 条/批 (默认 500, 减少单次 poll 时间)
        cfg.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);              // 立即返回 (默认 1)
        cfg.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);          // 最长等 500ms (默认 500)

        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> chatMessageListenerFactory(
            ConsumerFactory<String, ChatMessageEvent> cf) {
        ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}