#!/usr/bin/env bash
# v2.2.31 微信 OAuth 端到端自检
#
# 验证项：
# 1. cs-auth 启动 + actuator/health UP
# 2. /auth/wechat-oa/authorize 不带 redirect_uri 也能 302
# 3. /auth/wechat-oa/authorize-json 返回 JSON { url, state }
# 4. mock 模式：URL 含 MOCK-xxx
# 5. /auth/wechat-oa/callback-json?code=MOCK-xxx 返回 token
# 6. Gateway → cs-auth 完整链路
# 7. JwtUtils 启动日志长度 ≥ 32

set -e

AUTH_URL="${AUTH_URL:-http://127.0.0.1:9001}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:9000}"
PASS=0
FAIL=0

ok() { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.31 微信 OAuth 端到端自检"
echo " auth:    $AUTH_URL"
echo " gateway: $GATEWAY_URL"
echo "============================================================"

# --- 1. cs-auth health ---
echo
echo "[1/7] cs-auth health"
H=$(curl -s --max-time 3 "$AUTH_URL/actuator/health")
echo "$H" | grep -q '"status":"UP"' && ok "cs-auth UP" || fail "cs-auth not UP: $H"

# --- 2. authorize 不带 redirectUri ---
echo
echo "[2/7] /auth/wechat-oa/authorize (无 redirect_uri)"
RESP=$(curl -s -o /dev/null -w "%{http_code} %{redirect_url}" --max-time 5 "$AUTH_URL/auth/wechat-oa/authorize")
echo "  HTTP=$RESP"
if echo "$RESP" | grep -q "302\|MOCK-"; then
    ok "302 + mock code 生成"
else
    fail "没拿到 302: $RESP"
fi

# --- 3. authorize-json 返回 JSON ---
echo
echo "[3/7] /auth/wechat-oa/authorize-json"
RESP=$(curl -s --max-time 5 "$AUTH_URL/auth/wechat-oa/authorize-json")
echo "  resp=$RESP"
echo "$RESP" | grep -q '"url":' && ok "返回 url 字段" || fail "无 url 字段"
echo "$RESP" | grep -q '"code":0' && ok "code=0" || fail "code != 0"
echo "$RESP" | grep -qE 'MOCK-|open\.weixin\.qq\.com' && ok "url 是 mock 或真实微信" || fail "url 格式不对"

# --- 4. authorize 带 redirect_uri ---
echo
echo "[4/7] /auth/wechat-oa/authorize?redirect_uri=..."
RESP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "$AUTH_URL/auth/wechat-oa/authorize?redirect_uri=http://127.0.0.1:9001/auth/wechat-oa/callback-json")
echo "  HTTP=$RESP"
[ "$RESP" = "302" ] && ok "带 redirect_uri 返回 302" || fail "HTTP=$RESP (期望 302)"

# --- 5. callback-json 拿 token ---
echo
echo "[5/7] /auth/wechat-oa/callback-json?code=MOCK-xxx"
RESP=$(curl -s --max-time 5 "$AUTH_URL/auth/wechat-oa/callback-json?code=MOCK-TEST-E2E")
echo "  resp=${RESP:0:200}..."
echo "$RESP" | grep -q '"token":' && ok "返回 token" || fail "无 token"
echo "$RESP" | grep -q '"customerId":' && ok "返回 customerId" || fail "无 customerId"
echo "$RESP" | grep -q '"openid":' && ok "返回 openid" || fail "无 openid"

# --- 6. Gateway → cs-auth ---
echo
echo "[6/7] Gateway → cs-auth 完整链路"
H=$(curl -s --max-time 3 "$GATEWAY_URL/actuator/health")
echo "$H" | grep -q '"status":"UP"' && ok "cs-gateway UP" || fail "cs-gateway: $H"

RESP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "$GATEWAY_URL/auth/wechat-oa/authorize-json")
[ "$RESP" = "200" ] && ok "Gateway → cs-auth authorize-json 返回 200" || fail "HTTP=$RESP (期望 200)"

# --- 7. JwtUtils secret 长度校验 ---
echo
echo "[7/7] JwtUtils secret 长度（启动日志应打印 secret_len >= 32）"
echo "  → 请手动检查 cs-auth 启动日志: 'secret_len='"

# --- 汇总 ---
echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

exit $FAIL