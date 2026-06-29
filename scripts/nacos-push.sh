#!/usr/bin/env bash
# v2.2.94: 通用 Nacos 推送脚本 (任意 service)
#
# 用法:
#   nacos-push.sh <service> [nacos-addr] [namespace]
#
# 例子:
#   nacos-push.sh cs-auth
#   nacos-push.sh cs-im 192.168.1.100:8848 prod
#
# 默认从 docs/nacos-<service>-prod-fixed.yaml 读取, 推送到 Nacos
# 推送前自动:
#   1. yaml-dup-check.py 重复 key 检查
#   2. yaml 语法检查
#   3. 缺失 JWT_SECRET 时警告 (生产必须设)

set -e

SERVICE="${1:?Usage: $0 <service> [nacos-addr] [namespace]}"
NACOS_ADDR="${2:-127.0.0.1:8848}"
NACOS_NS="${3:-${NACOS_NAMESPACE:-prod}}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

ROOT="/workspace/online-chat"
YAML_FILE="$ROOT/docs/nacos-${SERVICE}-prod-fixed.yaml"

if [ ! -f "$YAML_FILE" ]; then
    echo "✗ 找不到 $YAML_FILE"
    echo "  请先创建 docs/nacos-${SERVICE}-prod-fixed.yaml"
    echo "  或用 scripts/yaml-nacos-validate.sh 看本地 application*.yml 内容"
    exit 1
fi

echo "=== 推送 ${SERVICE}-prod.yaml 到 Nacos ==="
echo "  addr: $NACOS_ADDR"
echo "  namespace: $NACOS_NS"
echo "  yaml: $YAML_FILE ($(wc -c < $YAML_FILE) bytes)"
echo

# 1. 重复 key 检查
echo "1) 重复 key 检查:"
if python3 "$ROOT/scripts/yaml-dup-check.py" "$YAML_FILE"; then
    echo "   ✓"
else
    echo "   ✗ 重复 key 存在, 推送会被 Nacos 拒绝"
    exit 1
fi
echo

# 2. JWT_SECRET 检查
echo "2) JWT_SECRET 检查:"
if grep -q "JWT_SECRET" "$YAML_FILE"; then
    echo "   ✓ JWT_SECRET 已配置"
else
    echo "   ⚠ JWT_SECRET 缺失 (生产环境必须显式配置, 避免 fallback 默认值)"
fi
echo

# 3. 推送
echo "3) POST 到 Nacos:"
NACOS_URL="http://${NACOS_ADDR}/nacos/v1/cs/configs"
HTTP=$(curl -s -m 10 -o /tmp/nacos-push-resp.txt -w '%{http_code}' \
    -X POST \
    -u "${NACOS_USER}:${NACOS_PASSWORD}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "dataId=${SERVICE}-prod.yaml" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=${NACOS_NS}" \
    --data-urlencode "content@${YAML_FILE}" \
    "${NACOS_URL}")

echo "   HTTP=$HTTP"
cat /tmp/nacos-push-resp.txt
echo

if [ "$HTTP" = "200" ]; then
    echo "4) ✓ 推送成功"
    echo "   建议重启 ${SERVICE}: kill -9 \$(cat /tmp/${SERVICE}.pid) && ./start.sh"
else
    echo "4) ✗ 推送失败 (HTTP $HTTP)"
    exit 1
fi
