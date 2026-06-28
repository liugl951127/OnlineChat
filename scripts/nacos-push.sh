#!/usr/bin/env bash
# =====================================================
# Nacos 远程配置一键推送脚本 (v2.2.57)
# =====================================================
#
# 用法:
#   bash scripts/nacos-push.sh gateway    # 推送 cs-gateway.yaml
#   bash scripts/nacos-push.sh auth       # 推送 cs-auth.yaml
#   bash scripts/nacos-push.sh im         # 推送 cs-im.yaml
#   bash scripts/nacos-push.sh message    # 推送 cs-message.yaml
#   bash scripts/nacos-push.sh all        # 推送全部
#
# 环境变量:
#   NACOS_ADDR      - nacos 地址 (默认 127.0.0.1:8848)
#   NACOS_USER      - nacos 用户名 (默认 nacos)
#   NACOS_PASSWORD  - nacos 密码 (默认 nacos)
#   NACOS_NAMESPACE - 命名空间 ID (默认空 = public)
#
# 重要: nacos 2.x 启用鉴权 (NACOS_AUTH_ENABLE=true) 后,
#       必须传用户名密码. 首次启动 nacos 可能随机密码,
#       需要用 mysql 重置或控制台修改.
# =====================================================

set -e

# 默认值
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"

DOCS_DIR="$(dirname "$0")/../docs"

push_config() {
    local svc="$1"
    local file="$DOCS_DIR/nacos-$svc.yaml"
    if [ ! -f "$file" ]; then
        echo "❌ $file 不存在"
        return 1
    fi

    echo "▶ 推送 $svc 配置: $file"
    local content=$(cat "$file")

    # 用 -u 提供 basic auth, --data-urlencode 编码 content
    local response
    response=$(curl -s -X POST "http://$NACOS_ADDR/nacos/v1/cs/configs" \
        -u "${NACOS_USER}:${NACOS_PASSWORD}" \
        -d "dataId=$svc.yaml" \
        -d "group=DEFAULT_GROUP" \
        --data-urlencode "content=$content" \
        -d "type=yaml" \
        -d "tenant=$NACOS_NAMESPACE" \
        -w "\n%{http_code}" 2>&1)

    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -n -1)

    if [ "$http_code" = "200" ]; then
        echo "✓ $svc.yaml 已推送到 nacos (HTTP $http_code)"
        return 0
    else
        echo "❌ $svc.yaml 推送失败 (HTTP $http_code)"
        echo "   响应: $body"
        echo
        echo "   排查建议:"
        echo "   1. 检查 NACOS_USER/NACOS_PASSWORD 是否正确"
        echo "   2. 首次启动 nacos 可能随机密码, 需 mysql 重置:"
        echo "      mysql -uroot -proot123 nacos -e \\"
        echo "        \"UPDATE users SET password='\$2a\$10\$EuWPZHzz32dJN7jexIMNyeQ0bF8e8VdG1YvX5VKvF8cXJu5wV8Kqe' WHERE username='nacos';\""
        echo "   3. 用 control panel 登录修改密码"
        echo "   4. 关闭 nacos 鉴权: docker-compose 设 NACOS_AUTH_ENABLE=false"
        return 1
    fi
}

# 检查 nacos 连通性
check_nacos() {
    echo "▶ 检查 nacos 连通性: $NACOS_ADDR"
    local code=$(curl -s -o /dev/null -w "%{http_code}" "http://$NACOS_ADDR/nacos/" --max-time 5 2>&1)
    if [ "$code" = "200" ] || [ "$code" = "302" ]; then
        echo "✓ nacos 可达 (HTTP $code)"
    else
        echo "❌ nacos 不可达 (HTTP $code)"
        echo "   启动 nacos: docker run -d -p 8848:8848 -p 9848:9848 nacos/nacos-server:v2.3.2"
        exit 1
    fi
}

case "${1:-help}" in
    gateway|auth|im|message)
        check_nacos
        push_config "$1"
        ;;
    all)
        check_nacos
        for svc in gateway auth im message; do
            push_config "$svc"
        done
        ;;
    help|*)
        echo "Usage: $0 {gateway|auth|im|message|all}"
        echo
        echo "环境变量:"
        echo "  NACOS_ADDR=$NACOS_ADDR"
        echo "  NACOS_USER=$NACOS_USER"
        echo "  NACOS_PASSWORD=***"
        echo "  NACOS_NAMESPACE=$NACOS_NAMESPACE"
        echo
        echo "示例:"
        echo "  bash scripts/nacos-push.sh all"
        echo "  NACOS_USER=admin NACOS_PASSWORD=xxx bash scripts/nacos-push.sh gateway"
        exit 1
        ;;
esac

echo
echo "============================================================"
echo " 推送完成! 应用通过 spring.config.import 自动拉取"
echo "============================================================"
echo
echo "热更新:"
echo "  curl -X POST http://127.0.0.1:9000/actuator/refresh"
echo
echo "验证:"
echo "  curl http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=cs-gateway.yaml \\"
echo "    -u nacos:nacos"