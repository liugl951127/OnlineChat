#!/bin/bash
# 等服务 actuator/health 返回 UP（最长 120s）
# 用法: bash wait-ready.sh http://127.0.0.1:9001 120

URL=$1
MAX=${2:-120}

echo "⏳ 等待 $URL/actuator/health 返回 UP..."
for i in $(seq 1 $MAX); do
  RESP=$(curl -s --max-time 2 "$URL/actuator/health" 2>/dev/null)
  if echo "$RESP" | grep -q '"status":"UP"'; then
    echo "✅ [$i s] UP"
    exit 0
  fi
  sleep 1
done

echo "❌ [${MAX}s] 超时"
echo "最后响应: $RESP"
exit 1
