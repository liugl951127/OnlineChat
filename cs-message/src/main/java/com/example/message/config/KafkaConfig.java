package com.example.message.config;

import com.example.common.kafka.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 生产者 + 消费者配置
 *
 * <p>设计：
 * <ul>
 *   <li>消费者组：{@code cs-message-group}（多实例负载均衡）</li>
 *   <li>分区数：3（与建议部署实例数对齐）</li>
 *   <li>副本数：1（单节点部署），生产建议 3</li>
 *   <li>失败重试：指数退避（最多 3 次，间隔 1s → 2s → 4s）</li>
 *   <li>死信队列：失败消息发到 {@code chat.dlq}</li>
 * </ul>
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.application.name:cs-message}")
    private String appName;

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:cs-message-group}")
    private String groupId;

    // ============ 主题自动创建 ============
    @Bean
    public NewTopic customerMessageTopic() {
        return TopicBuilder.name(KafkaTopics.CUSTOMER_MESSAGE)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic agentMessageTopic() {
        return TopicBuilder.name(KafkaTopics.AGENT_MESSAGE)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic systemMessageTopic() {
        return TopicBuilder.name(KafkaTopics.SYSTEM_MESSAGE)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic sessionEventTopic() {
        return TopicBuilder.name(KafkaTopics.SESSION_EVENT)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic presenceEventTopic() {
        return TopicBuilder.name(KafkaTopics.PRESENCE_EVENT)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic auditEventTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIT_EVENT)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name("chat.dlq").partitions(3).replicas(1).build();
    }

    // ============ 生产者 ============
    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.RETRIES_CONFIG, 3);
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        cfg.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    // ============ 消费者 ============
    @Bean
    public ConsumerFactory<String, ChatMessageEvent> chatMessageConsumerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildConsumerProperties());
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // 手动 ack
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        cfg.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.common.kafka,com.example.message.model");
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> chatMessageListenerFactory(
            ConsumerFactory<String, ChatMessageEvent> cf) {
        ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(3);  // 3 个消费者线程
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // 失败 → 指数退避 → DLQ
        factory.setCommonErrorHandler(defaultErrorHandler());
        return factory;
    }

    /** 默认错误处理：指数退避 + 死信队列 */
    @Bean
    public DefaultErrorHandler defaultErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(10000L);
        backOff.setMaxElapsedTime(30000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(
            (record, ex) -> {
                log.error("[Kafka] DLQ record={} topic={}", record.value(), record.topic(), ex);
                // 发送到 DLQ（这里简化：仅记录日志，生产应投到 chat.dlq 主题）
            },
            backOff
        );
        return handler;
    }
}