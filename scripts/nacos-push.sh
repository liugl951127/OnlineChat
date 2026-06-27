#!/usr/bin/env bash
# =====================================================
# Nacos 远程配置一键推送脚本 (v2.2.49)
# =====================================================
#
# 用法:
#   bash scripts/nacos-push.sh gateway    # 推送 cs-gateway.yaml
#   bash scripts/nacos-push.sh auth       # 推送 cs-auth.yaml
#   bash scripts/nacos-push.sh im         # 推送 cs-im.yaml
#   bash scripts/nacos-push.sh message    # 推送 cs-message.yaml
#   bash scripts/nacos-push.sh all        # 推送全部
# =====================================================

set -e
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
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
    curl -s -X POST "http://$NACOS_ADDR/nacos/v1/cs/configs" \
        -d "dataId=$svc.yaml" \
        -d "group=DEFAULT_GROUP" \
        --data-urlencode "content=$content" \
        -d "type=yaml" \
        -d "tenant=" | head -c 100
    echo
    echo "✓ $svc.yaml 已推送到 nacos"
}

case "${1:-help}" in
    gateway|auth|im|message)
        push_config "$1"
        ;;
    all)
        for svc in gateway auth im message; do
            push_config "$svc"
        done
        ;;
    help|*)
        echo "Usage: $0 {gateway|auth|im|message|all}"
        echo
        echo "需要 nacos 在 $NACOS_ADDR 运行"
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