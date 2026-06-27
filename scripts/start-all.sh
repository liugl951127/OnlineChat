#!/bin/bash
# =====================================================
# OnlineChat 一键启动 4 个微服务（开发模式，无 Nacos）
# 用法：bash start-all.sh
# =====================================================

set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)

# 共享参数：禁用 Nacos（生产去掉）
NACOS_ARGS="--spring.cloud.nacos.discovery.enabled=false \
            --spring.cloud.nacos.config.enabled=false \
            --spring.cloud.nacos.config.import-check.enabled=false"

mkdir -p logs

start_one() {
  local name=$1
  local jar=$2
  local extra=$3
  local logfile="logs/${name}.log"

  echo "🚀 启动 $name ..."
  nohup java -jar "$jar" $NACOS_ARGS $extra > "$logfile" 2>&1 &
  local pid=$!
  echo "$pid" > "logs/${name}.pid"
  echo "   PID=$pid  log=$logfile"
}

start_one cs-gateway cs-gateway/target/cs-gateway-1.7.1.jar \
  "--spring.main.web-application-type=reactive --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"

start_one cs-auth cs-auth/target/cs-auth-1.7.1.jar ""

start_one cs-im cs-im/target/cs-im-1.7.1.jar ""

start_one cs-message cs-message/target/cs-message-1.7.1.jar ""

sleep 20
echo ""
echo "✅ 启动完成，检查端口..."
ss -tnlp 2>/dev/null | grep -E ":9000|:9001|:9003|:9005" | head -10
echo ""
echo "📋 进程状态："
for f in logs/*.pid; do
  pid=$(cat "$f")
  name=$(basename "$f" .pid)
  if ps -p "$pid" > /dev/null 2>&1; then
    echo "   ✅ $name (PID=$pid) alive"
  else
    echo "   ❌ $name (PID=$pid) dead - see logs/$name.log"
  fi
done

echo ""
echo "🔍 健康检查："
echo "   Gateway:  curl http://localhost:9000/wx-oa-h5.html"
echo "   Auth:     curl http://localhost:9001/actuator/health"
echo "   IM:       curl http://localhost:9003/actuator/health"
echo ""
echo "🛑 停止全部：bash scripts/stop-all.sh"