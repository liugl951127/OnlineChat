FROM eclipse-temurin:17-jre-jammy

LABEL service="cs-message"

ENV SERVER_PORT=9005
ENV TZ=Asia/Shanghai

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl tini && rm -rf /var/lib/apt/lists/*

COPY cs-message/target/cs-message-*.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsSL http://localhost:9005/actuator/health || exit 1

EXPOSE 9005

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]


# ============ Kafka 主题初始化（首次启动）============
FROM bitnami/kafka:3.7 AS kafka-init

# 自动创建主题：cs-message 启动后会触发 @Bean NewTopic 自动创建