FROM eclipse-temurin:17-jre-jammy

LABEL service="cs-gateway"

ENV SERVER_PORT=9000
ENV TZ=Asia/Shanghai

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl tini && rm -rf /var/lib/apt/lists/*

COPY cs-gateway/target/cs-gateway-*.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsSL http://localhost:9000/actuator/health || exit 1

EXPOSE 9000

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
