#!/usr/bin/env bash
# =====================================================
# OnlineChat 端到端合规化购买演示 (v2.2.42)
# =====================================================
#
# 演示场景 (v2.2.43 加签约):
#   1. 客户 c-customer004 登录 (有 KYC + 持仓)
#   2. 风险评估 (适度型)
#   3. 购买 50000 元 稳健理财 1 号
#   4. 4 道合规检查 (实名/风险/适配/AML)
#   5. 电子签约 (生成合同 + RSA-PSS 签名)
#   6. 一键支付 (mock 银行)
#   7. 查询持仓变化
#
# 用法:
#   bash scripts/e2e-compliance-demo.sh
# =====================================================

set -e
cd "$(dirname "$0")/.."

# 配置
GATEWAY="${GATEWAY:-http://127.0.0.1:9000}"
CS_IM="${CS_IM:-http://127.0.0.1:9003}"
CUSTOMER_ID="${CUSTOMER_ID:-c-customer004}"
USERNAME="${USERNAME:-customer004}"
PASSWORD="${PASSWORD:-pass123}"

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.42 合规化购买端到端演示"
echo " gateway:  $GATEWAY"
echo " cs-im:    $CS_IM"
echo " customer: $CUSTOMER_ID"
echo "============================================================"

# ========== 1. 登录 ==========
echo
echo "[1/10] 客户 $USERNAME 登录"

LOGIN_RESP=$(curl -s -X POST "$GATEWAY/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))")
if [ -n "$TOKEN" ]; then
    ok "登录成功"
else
    fail "登录失败: $LOGIN_RESP"
    exit 1
fi
CUSTOMER_ID=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('customerId',''))")
echo "  → customerId=$CUSTOMER_ID"

# ========== 2. KYC 状态查询 ==========
echo
echo "[2/10] 查询 KYC 状态"
KYC_RESP=$(curl -s "$CS_IM/kyc/my-status?customerId=$CUSTOMER_ID" 2>&1 | head -c 500)
echo "  resp: $KYC_RESP"
if echo "$KYC_RESP" | grep -qE '"status":"COMPLETED"|"completed":true|"status":".*COMPLETED' ; then
    ok "KYC 已完成 (COMPLETED)"
elif echo "$KYC_RESP" | grep -qE '"data":|"code":0' ; then
    warn=$(echo "$KYC_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null)
    echo "  KYC 状态: $warn (演示可继续，未完成则后端会拒绝)"
else
    echo "  KYC 端点不可达 (继续演示)"
fi

# ========== 3. 风险评估 ==========
echo
echo "[3/10] 提交风险评估问卷 (保守型)"
RISK_RESP=$(curl -s -X POST "$CS_IM/risk/assess" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\":\"$CUSTOMER_ID\",
    \"answers\":{
      \"年龄\":\"30-50岁\",
      \"年收入\":\"10-30万\",
      \"投资经验\":\"1-3年\",
      \"投资目标\":\"稳健增值\",
      \"风险承受\":\"不愿亏损\",
      \"流动性\":\"长期不用\"
    }
  }")
echo "  resp: ${RISK_RESP:0:300}"
RISK_LEVEL=$(echo "$RISK_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('riskLevel',''))" 2>/dev/null)
if [ -n "$RISK_LEVEL" ]; then
    ok "风险评估结果: $RISK_LEVEL"
else
    fail "风险评估失败"
    echo "$RISK_RESP"
fi

# ========== 4. 创建订单 ==========
echo
echo "[4/10] 创建订单 (稳健理财 1 号 50000元)"
ORDER_RESP=$(curl -s -X POST "$CS_IM/order/create" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\":\"$CUSTOMER_ID\",
    \"productCode\":\"P-W001\",
    \"amount\":50000
  }")
echo "  resp: ${ORDER_RESP:0:300}"
ORDER_NO=$(echo "$ORDER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('orderNo',''))" 2>/dev/null)
if [ -n "$ORDER_NO" ]; then
    ok "订单创建成功: $ORDER_NO"
else
    fail "订单创建失败"
    echo "$ORDER_RESP"
fi

# ========== 5. 风险评估 (order 级别) ==========
echo
echo "[5/10] 订单风险评估"
ASSESS_RESP=$(curl -s -X POST "$CS_IM/order/$ORDER_NO/assess")
echo "  resp: ${ASSESS_RESP:0:200}"
if echo "$ASSESS_RESP" | grep -q '"status":"RISK_ASSESSED"'; then
    ok "RISK_ASSESSED"
else
    fail "未通过风险评估"
fi

# ========== 6. 合规检查 ==========
echo
echo "[6/10] 合规检查 (4 道关: 实名/风险/适配/AML)"
COMP_RESP=$(curl -s -X POST "$CS_IM/order/$ORDER_NO/compliance")
echo "  resp: ${COMP_RESP:0:500}"
COMPLIANCE=$(echo "$COMP_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('complianceResult',''))" 2>/dev/null)
if [ "$COMPLIANCE" = "PASS" ]; then
    ok "✓ 合规检查全部通过 (PASS)"
elif [ "$COMPLIANCE" = "REJECTED" ]; then
    REJECT_REASON=$(echo "$COMP_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('complianceRemark',''))" 2>/dev/null)
    fail "合规检查未通过: $REJECT_REASON"
    echo "  → KYC 未完成 / 风险等级不够 / 金额超限"
else
    warn "合规检查未知结果: $COMP_RESP"
fi

# ========== 7. 生成合同 (v2.2.43 签约) ==========
echo
echo "[7/10] 生成电子合同 (合同编号 + SHA256 hash)"
if [ "$COMPLIANCE" = "PASS" ]; then
    CONT_RESP=$(curl -s -X POST "$CS_IM/order/$ORDER_NO/contract/generate" \
      -H "Content-Type: application/json" \
      -d '{"templateId":"TPL-FIN-001"}')
    echo "  resp: ${CONT_RESP:0:400}"
    CONTRACT_NO=$(echo "$CONT_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('contractNo',''))" 2>/dev/null)
    HASH=$(echo "$CONT_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('contentHash',''))" 2>/dev/null)
    if [ -n "$CONTRACT_NO" ] && [ -n "$HASH" ]; then
        ok "✓ 合同生成: $CONTRACT_NO (hash: ${HASH:0:16}...)"
    else
        fail "合同生成失败"
    fi
else
    echo "  ⊘ 跳过合同生成 (合规未通过)"
fi

# ========== 8. 客户签约 (v2.2.43 RSA-PSS 签名) ==========
echo
echo "[8/10] 客户签约 (RSA-PSS-SHA256 mock 签名)"
if [ -n "$CONTRACT_NO" ]; then
    SIGN_RESP=$(curl -s -X POST "$CS_IM/order/$ORDER_NO/contract/sign" \
      -H "Content-Type: application/json" \
      -d "{
        \"contractNo\":\"$CONTRACT_NO\",
        \"publicKey\":\"-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuMock\n-----END PUBLIC KEY-----\",
        \"signature\":\"MOCK_RSA_PSS_SIGNATURE_BASE64\",
        \"signedIp\":\"127.0.0.1\"
      }")
    echo "  resp: ${SIGN_RESP:0:300}"
    if echo "$SIGN_RESP" | grep -q '"status":"CONTRACT_SIGNED"'; then
        ok "✓ 签约成功 → CONTRACT_SIGNED"
    else
        fail "签约失败"
    fi
else
    echo "  ⊘ 跳过签约 (无合同)"
fi

# ========== 9. 支付 ==========
echo
echo "[9/10] 支付 (mock 银行)"
if [ "$COMPLIANCE" = "PASS" ] && [ -n "$CONTRACT_NO" ]; then
    PAY_RESP=$(curl -s -X POST "$CS_IM/order/$ORDER_NO/pay" \
      -H "Content-Type: application/json" \
      -d '{"method":"MOCK_BANK"}')
    echo "  resp: ${PAY_RESP:0:300}"
    if echo "$PAY_RESP" | grep -q '"status":"SETTLED"'; then
        ok "✓ 支付成功，订单 SETTLED + 持仓已生成"
    else
        fail "支付失败 (检查是否需要先签约)"
    fi
else
    echo "  ⊘ 跳过支付"
fi

# ========== 8. 查询持仓 ==========
echo
echo "[10/10] 查询客户持仓"
HOLD_RESP=$(curl -s "$CS_IM/order/holdings?customerId=$CUSTOMER_ID")
echo "  resp: ${HOLD_RESP:0:500}"
HOLDING_COUNT=$(echo "$HOLD_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])))" 2>/dev/null)
if [ -n "$HOLDING_COUNT" ] && [ "$HOLDING_COUNT" -ge 0 ]; then
    ok "持仓数: $HOLDING_COUNT"
fi

# ========== 汇总 ==========
echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -eq 0 ]; then
    echo
    echo "🎉 端到端合规化购买演示通过！"
    echo
    echo "订单状态: COMPLIANCE_PASSED → SETTLED"
    echo "持仓已生成: /cs-im/order/holdings?customerId=$CUSTOMER_ID"
    echo
    echo "💡 试试看:"
    echo "   - 单笔 6 万 → AML 单笔超限 → REJECTED"
    echo "   - 同一天 4 笔 5 万 → AML 单日累计超限 → REJECTED"
    echo "   - 进取型客户买稳健 → 适当性通过"
    echo "   - 保守型客户买进取 → 适当性失败 → REJECTED"
fi

exit $FAIL