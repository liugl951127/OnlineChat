#!/bin/bash
###############################################################################
# OnlineChat 生产环境部署脚本
#
# 用法：
#   bash scripts/deploy-prod.sh start    # 启动所有服务
#   bash scripts/deploy-prod.sh stop     # 停止所有服务
#   bash scripts/deploy-prod.sh status   # 查看状态
#   bash scripts/deploy-prod.sh restart  # 重启
#
# 环境变量（必须设置）：
#   DB_HOST, DB_USER, DB_PASSWORD
#   REDIS_HOST, REDIS_PASSWORD
#   KAFKA_BOOTSTRAP
#   NACOS_ADDR, NACOS_NAMESPACE, NACOS_USER, NACOS_PASS
#   JWT_SECRET, PHONE_AES_SECRET
#   WX_OA_APP_ID, WX_OA_APP_SECRET
#   BAIDU_OCR_APP_ID, BAIDU_OCR_API_KEY, BAIDU_OCR_SECRET_KEY
#   TENCENT_SECRET_ID, TENCENT_SECRET_KEY
#   LLM_API_KEY
#   PUBLIC_URL (e.g. https://yourdomain.com)
###############################################################################

set -e

APP_HOME="/opt/onlinechat"
LOG_DIR="/var/log/onlinechat"
JAR_DIR="$APP_HOME/jars"

# 服务端口
GATEWAY_PORT=${GATEWAY_PORT:-9000}
AUTH_PORT=${AUTH_PORT:-9001}
IM_PORT=${IM_PORT:-9003}
MESSAGE_PORT=${MESSAGE_PORT:-9005}

# 启动函数
start_service() {
  local name=$1
  local port=$2
  local jar=$3
  local profile=${4:-prod}

  if [ ! -f "$jar" ]; then
    echo "❌ jar not found: $jar"
    return 1
  fi

  echo "▶ Starting $name on port $port..."
  mkdir -p "$LOG_DIR"
  nohup java -Xms512m -Xmx2g \
    -jar "$jar" \
    --spring.profiles.active="$profile" \
    --server.port="$port" \
    > "$LOG_DIR/$name.log" 2>&1 &

  echo $! > "/tmp/onlinechat-$name.pid"
  sleep 1
  echo "✅ $name started (PID=$(cat /tmp/onlinechat-$name.pid))"
}

# 停止函数
stop_service() {
  local name=$1
  local pidfile="/tmp/onlinechat-$name.pid"

  if [ -f "$pidfile" ]; then
    local pid=$(cat "$pidfile")
    echo "▶ Stopping $name (PID=$pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 3
    kill -9 "$pid" 2>/dev/null || true
    rm -f "$pidfile"
  else
    echo "⚠️  $name not running (no pidfile)"
  fi
}

# 状态函数
status_service() {
  local name=$1
  local port=$2
  local pidfile="/tmp/onlinechat-$name.pid"

  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    local pid=$(cat "$pidfile")
    local health=$(curl -s --max-time 2 "http://localhost:$port/actuator/health" | grep -o '"status":"[^"]*"' | head -1 || echo "unreachable")
    echo "✅ $name: PID=$pid, port=$port, $health"
  else
    echo "❌ $name: not running"
  fi
}

# 主命令
case "$1" in
  start)
    echo "============================================================"
    echo " 启动 OnlineChat 生产服务"
    echo "============================================================"
    [ -z "$DB_PASSWORD" ] && { echo "❌ DB_PASSWORD not set"; exit 1; }
    [ -z "$JWT_SECRET" ] && { echo "❌ JWT_SECRET not set"; exit 1; }

    start_service "cs-auth"    $AUTH_PORT    "$JAR_DIR/cs-auth-1.7.1.jar"
    start_service "cs-im"      $IM_PORT      "$JAR_DIR/cs-im-1.7.1.jar"
    start_service "cs-message" $MESSAGE_PORT "$JAR_DIR/cs-message-1.7.1.jar"
    sleep 10  # 等后端服务注册到 Nacos
    start_service "cs-gateway" $GATEWAY_PORT "$JAR_DIR/cs-gateway-1.7.1.jar"

    echo
    echo "等待服务就绪..."
    sleep 15
    echo
    bash "$0" status
    ;;

  stop)
    echo "============================================================"
    echo " 停止 OnlineChat 生产服务"
    echo "============================================================"
    stop_service "cs-gateway"
    stop_service "cs-auth"
    stop_service "cs-im"
    stop_service "cs-message"
    echo "✅ 所有服务已停止"
    ;;

  restart)
    bash "$0" stop
    sleep 5
    bash "$0" start
    ;;

  status)
    echo "============================================================"
    echo " OnlineChat 服务状态"
    echo "============================================================"
    status_service "cs-gateway" $GATEWAY_PORT
    status_service "cs-auth"    $AUTH_PORT
    status_service "cs-im"      $IM_PORT
    status_service "cs-message" $MESSAGE_PORT
    ;;

  *)
    echo "用法: $0 {start|stop|restart|status}"
    exit 1
    ;;
esac