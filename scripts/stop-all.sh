#!/bin/bash
# 停止全部微服务
cd "$(dirname "$0")/.."

if [ -d logs ]; then
  for f in logs/*.pid; do
    [ -e "$f" ] || continue
    pid=$(cat "$f")
    name=$(basename "$f" .pid)
    if ps -p "$pid" > /dev/null 2>&1; then
      echo "🛑 停止 $name (PID=$pid) ..."
      kill "$pid"
    fi
    rm -f "$f"
  done
fi
# 兜底：杀所有 cs-* jar 进程
pkill -9 -f "cs-gateway.*\.jar" 2>/dev/null
pkill -9 -f "cs-auth.*\.jar" 2>/dev/null
pkill -9 -f "cs-im.*\.jar" 2>/dev/null
pkill -9 -f "cs-message.*\.jar" 2>/dev/null
echo "✅ 全部停止"