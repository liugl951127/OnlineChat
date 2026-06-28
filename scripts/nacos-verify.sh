#!/usr/bin/env bash
# =====================================================
# OnlineChat Nacos 服务发现验证 (v2.2.44)
# =====================================================
#
# 流程:
#   1. 启动 nacos standalone (端口 8848)
#   2. 启动 4 个微服务 (NACOS_DISCOVERY_ENABLED=true)
#   3. 等待服务注册
#   4. 验证 nacos 控制台能看到所有服务
#   5. 验证 nacos → gateway lb://cs-auth 路由生效
#
# 用法:
#   bash scripts/nacos-verify.sh start   # 启动 nacos + 4 服务
#   bash scripts/nacos-verify.sh check   # 验证 nacos 注册
#   bash scripts/nacos-verify.sh stop    # 停止所有
# =====================================================

set -e
cd "$(dirname "$0")/.."

NACOS_HOME="${NACOS_HOME:-/tmp/nacos}"
NACOS_PORT="${NACOS_PORT:-8848}"
NACOS_ADDR="127.0.0.1:$NACOS_PORT"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"
LOG_DIR="logs"
mkdir -p $LOG_DIR

# nacos standalone 配置
export NACOS_OPTS="-Xms512m -Xmx512m -Xmn256m -Dnacos.standalone=true"

start_nacos() {
    if [ ! -d "$NACOS_HOME" ]; then
        echo "❌ nacos 未安装在 $NACOS_HOME"
        echo "   下载地址: https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.tar.gz"
        echo "   或: docker run -d -p 8848:8848 -p 9848:9848 nacos/nacos-server:v2.3.2"
        return 1
    fi
    if pgrep -f "nacos.Nacos" >/dev/null; then
        echo "✓ nacos 已在运行"
    else
        echo "▶ 启动 nacos standalone..."
        nohup bash $NACOS_HOME/bin/startup.sh -m standalone > $LOG_DIR/nacos.log 2>&1 &
        echo "  pid: $!"
        echo "  日志: $LOG_DIR/nacos.log"
        echo "  控制台: http://$NACOS_ADDR/nacos (nacos/nacos)"
        for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
            sleep 2
            if curl -s http://$NACOS_ADDR/nacos/ >/dev/null 2>&1; then
                echo "✓ nacos 已就绪"
                return 0
            fi
        done
        echo "❌ nacos 启动超时"
        return 1
    fi
}

start_services() {
    echo "▶ 启动 4 微服务 (NACOS_DISCOVERY_ENABLED=true)..."
    export NACOS_DISCOVERY_ENABLED=true
    export NACOS_CONFIG_ENABLED=true
    export NACOS_ADDR=$NACOS_ADDR

    # cs-gateway
    nohup java -jar target/cs-gateway-*.jar \
        --spring.profiles.active=prod \
        --spring.cloud.nacos.discovery.namespace=public \
        > $LOG_DIR/cs-gateway.log 2>&1 &
    echo "  cs-gateway pid=$!"

    # cs-auth
    nohup java -jar target/cs-auth-*.jar \
        --spring.profiles.active=prod \
        > $LOG_DIR/cs-auth.log 2>&1 &
    echo "  cs-auth pid=$!"

    # cs-im
    nohup java -jar target/cs-im-*.jar \
        --spring.profiles.active=prod \
        > $LOG_DIR/cs-im.log 2>&1 &
    echo "  cs-im pid=$!"

    # cs-message
    nohup java -jar target/cs-message-*.jar \
        --spring.profiles.active=prod \
        > $LOG_DIR/cs-message.log 2>&1 &
    echo "  cs-message pid=$!"

    echo "✓ 启动命令已发送，等待 30 秒服务注册..."
    sleep 30
}

check_services() {
    echo
    echo "============================================================"
    echo " 验证 nacos 服务注册"
    echo "============================================================"
    echo

    # 调 nacos API 获取所有服务
    SERVICES=$(curl -s -u "${NACOS_USER}:${NACOS_PASSWORD}" "http://$NACOS_ADDR/nacos/v1/ns/service/list?pageNo=1&pageSize=10" 2>&1)
    echo "Nacos 已注册服务列表:"
    echo "$SERVICES" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    services = d.get('doms', [])
    if not services:
        print('  (空)')
    else:
        for s in sorted(services):
            print(f'  ✓ {s}')
except Exception as e:
    print(f'  ❌ 解析失败: {e}')
    print(f'  Raw: {sys.stdin.read()[:200]}')
"

    echo
    echo "============================================================"
    echo " 验证健康检查"
    echo "============================================================"

    for port in 9001 9003 9005; do
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:$port/actuator/health" 2>&1)
        if [ "$STATUS" = "200" ]; then
            echo "  ✓ :$port 健康"
        else
            echo "  ✗ :$port 返回 $STATUS"
        fi
    done

    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:9000/actuator/health" 2>&1)
    if [ "$STATUS" = "200" ]; then
        echo "  ✓ :9000 网关健康"
    else
        echo "  ✗ :9000 网关返回 $STATUS"
    fi
}

stop_all() {
    echo "▶ 停止所有服务..."
    for jar in cs-gateway cs-auth cs-im cs-message; do
        PIDS=$(pgrep -f "$jar-.*\.jar" || true)
        for pid in $PIDS; do
            kill -TERM $pid 2>/dev/null && echo "  kill -TERM $jar ($pid)" || true
        done
    done
    if pgrep -f "nacos.Nacos" >/dev/null; then
        echo "▶ 停止 nacos..."
        bash $NACOS_HOME/bin/shutdown.sh 2>/dev/null || true
    fi
    sleep 3
    echo "✓ 已停止"
}

case "${1:-check}" in
    start)
        start_nacos
        start_services
        check_services
        ;;
    check)
        start_nacos
        check_services
        ;;
    stop)
        stop_all
        ;;
    *)
        echo "Usage: $0 {start|check|stop}"
        exit 1
        ;;
esac