#!/usr/bin/env bash
# v2.2.89: 推送修复版 cs-auth-prod.yaml 到 Nacos
# 修复: spring.cloud.nacos.discovery + config 合并到单一 spring.cloud.nacos 节点
#
# 用法: ./nacos-push-cs-auth.sh [nacos-addr] [namespace]
#
# 默认从 docs/nacos-cs-auth-prod-fixed.yaml 读取内容, 推送到 Nacos

set -e

NACOS_ADDR="${1:-127.0.0.1:8848}"
NACOS_NS="${2:-${NACOS_NAMESPACE:-prod}}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

YAML_FILE="$(dirname $0)/../docs/nacos-cs-auth-prod-fixed.yaml"

if [ ! -f "$YAML_FILE" ]; then
    echo "✗ 找不到 $YAML_FILE"
    exit 1
fi

echo "=== 推送修复版 cs-auth-prod.yaml 到 Nacos ==="
echo "  addr: $NACOS_ADDR"
echo "  namespace: $NACOS_NS"
echo "  yaml: $YAML_FILE (大小 $(wc -c < $YAML_FILE) bytes)"
echo

# 1. 本地校验
echo "1) 本地重复 key 检查:"
python3 /workspace/online-chat/scripts/yaml-dup-check.py "$YAML_FILE"
echo

# 2. 推送
echo "2) POST 到 Nacos:"
NACOS_URL="http://${NACOS_ADDR}/nacos/v1/cs/configs"
HTTP=$(curl -s -m 10 -o /tmp/nacos-push-resp.txt -w '%{http_code}' \
    -X POST \
    -u "${NACOS_USER}:${NACOS_PASSWORD}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "dataId=cs-auth-prod.yaml" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=${NACOS_NS}" \
    --data-urlencode "content@${YAML_FILE}" \
    "${NACOS_URL}")

echo "   HTTP=$HTTP"
cat /tmp/nacos-push-resp.txt
echo

if [ "$HTTP" = "200" ]; then
    echo "3) ✓ 推送成功"
    echo "   建议重启 cs-auth: kill -9 \$(cat /tmp/cs-auth.pid) && ./start.sh"
else
    echo "3) ✗ 推送失败 (HTTP $HTTP)"
    exit 1
fi
