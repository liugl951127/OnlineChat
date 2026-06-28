#!/usr/bin/env bash
# =====================================================
# cs-message 路由端到端测试 (v2.2.56)
# =====================================================
#
# 测试场景:
#   1. 直连 :9005 测试所有端点 (绕过 gateway)
#   2. 通过 :9000 gateway 测试 (走 lb://cs-message)
#   3. 验证两个路径都返回 200
#
# 用法:
#   bash scripts/e2e-message-routes.sh
# =====================================================

set -e
GATEWAY="${GATEWAY:-http://127.0.0.1:9000}"
CS_MESSAGE="${CS_MESSAGE:-http://127.0.0.1:9005}"

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " cs-message 路由测试 (v2.2.56)"
echo "============================================================"
echo "Gateway:  $GATEWAY"
echo "Direct:   $CS_MESSAGE"
echo

# 测试端点列表
test_endpoint() {
    local url="$1"
    local expected_path="$2"
    local desc="$3"
    
    local status=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "$url" 2>&1)
    if [ "$status" = "200" ]; then
        ok "$desc → $status"
    elif [ "$status" = "404" ]; then
        fail "$desc → 404 (路径不存在)"
    elif [ "$status" = "503" ]; then
        fail "$desc → 503 (nacos 不可达, lb 解析失败)"
    else
        fail "$desc → $status"
    fi
}

echo "[1/2] 直连 :9005 (绕过 gateway)"
echo "  场景: cs-message 自己处理请求, 不经过 gateway"
test_endpoint "$CS_MESSAGE/message/offline/c-demo"            "/message/offline/{userId}"      "drain offline"
test_endpoint "$CS_MESSAGE/message/offline/c-demo/peek"      "/message/offline/{userId}/peek" "peek offline"
test_endpoint "$CS_MESSAGE/message/offline/c-demo/count"     "/message/offline/{userId}/count" "count offline"
test_endpoint "$CS_MESSAGE/message/offline/list?userId=c-demo" "/message/offline/list"        "list offline (OfflineCtrl)"
test_endpoint "$CS_MESSAGE/message/offline/size?userId=c-demo"  "/message/offline/size"         "size offline (OfflineCtrl)"
test_endpoint "$CS_MESSAGE/message/presence/c-demo"          "/message/presence/{userId}"      "presence check"
test_endpoint "$CS_MESSAGE/message/presence/online-count"    "/message/presence/online-count" "online count"

echo
echo "[2/2] 通过 :9000 gateway (走 lb://cs-message)"
echo "  场景: gateway 通过 nacos 服务发现 + loadbalancer"
test_endpoint "$GATEWAY/message/offline/c-demo"            "/message/offline/{userId}"      "drain offline via gw"
test_endpoint "$GATEWAY/message/offline/c-demo/peek"      "/message/offline/{userId}/peek" "peek offline via gw"
test_endpoint "$GATEWAY/message/offline/c-demo/count"     "/message/offline/{userId}/count" "count offline via gw"
test_endpoint "$GATEWAY/message/offline/list?userId=c-demo" "/message/offline/list"        "list offline via gw (OfflineCtrl)"
test_endpoint "$GATEWAY/message/offline/size?userId=c-demo"  "/message/offline/size"         "size offline via gw (OfflineCtrl)"
test_endpoint "$GATEWAY/message/presence/c-demo"          "/message/presence/{userId}"      "presence via gw"
test_endpoint "$GATEWAY/message/presence/online-count"    "/message/presence/online-count" "online count via gw"

echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -eq 0 ]; then
    echo
    echo "🎉 所有路由正常！"
    echo
    echo "路由匹配链:"
    echo "  /message/offline/{userId}   → cs-gateway:9000"
    echo "    → lb://cs-message (nacos 解析到 :9005)"
    echo "    → cs-message:9005/message/offline/{userId}"
    echo "    → MessageController.drain()"
elif [ $FAIL -ge 7 ]; then
    echo
    echo "❌ 7+ 项 gateway 测试失败, 大概率 nacos 不可达"
    echo "   启动 nacos: docker run -d -p 8848:8848 nacos/nacos-server:v2.3.2"
fi

exit $FAIL