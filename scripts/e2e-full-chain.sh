#!/usr/bin/env bash
# =====================================================
# 完整链路端到端模拟 (v2.2.78)
# =====================================================
#
# 模拟完整客服会话交易流程, 含视频回溯:
#   1. 客户登录拿 csrf + token
#   2. 坐席登录
#   3. 获取活跃会话
#   4. 坐席接管会话
#   5. 客户/坐席双向聊天
#   6. 客户上传 5 帧截图 (SCREENSHOT + INTERACTION)
#   7. 触发视频合成
#   8. 轮询等 SUCCESS
#   9. 验证 MP4 可下载
#  10. DB 验证 replay_frame + replay_job + video_replay_url
#
# 用法:
#   AUTH=http://127.0.0.1:9003 IM=http://127.0.0.1:9001 bash scripts/e2e-full-chain.sh
# =====================================================

set -e

AUTH="${AUTH:-http://127.0.0.1:9003}"
IM="${IM:-http://127.0.0.1:9001}"
COOKIE_CUST="/tmp/cs-cust.cookie"
COOKIE_AGENT="/tmp/cs-agent.cookie"
PNG_DATA="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

echo "============================================================"
echo " 完整链路端到端模拟 (v2.2.78)"
echo "============================================================"
echo " AUTH: $AUTH"
echo " IM  : $IM"

# ============================================================
# Step 1: 客户登录
# ============================================================
echo
echo "[1/10] 客户登录 (customer001)"
CUST_LOGIN=$(curl -s -c $COOKIE_CUST -X POST $AUTH/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"customer001","password":"pass123"}')
CUST_CODE=$(echo $CUST_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['code'])")
[ "$CUST_CODE" = "0" ] || { echo "[FAIL] $CUST_LOGIN"; exit 1; }

CUST_CSRF=$(echo $CUST_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['csrf'])")
CUST_ID=$(echo $CUST_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['customerId'])")
CUST_TOKEN=$(echo $CUST_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")
echo "  ✓ 客户: $CUST_ID, csrf=${CUST_CSRF:0:20}..."

# ============================================================
# Step 2: 坐席登录
# ============================================================
echo
echo "[2/10] 坐席登录 (agent001)"
AGENT_LOGIN=$(curl -s -c $COOKIE_AGENT -X POST $AUTH/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"agent001","password":"pass123"}')
AGENT_CODE=$(echo $AGENT_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['code'])")
[ "$AGENT_CODE" = "0" ] || { echo "[FAIL] $AGENT_LOGIN"; exit 1; }

AGENT_CSRF=$(echo $AGENT_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['csrf'])")
AGENT_USERNAME=$(echo $AGENT_LOGIN | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['username'])")
echo "  ✓ 坐席: $AGENT_USERNAME, csrf=${AGENT_CSRF:0:20}..."

# ============================================================
# 通用 header helper
# ============================================================
CUST_H=(-H "X-CSRF-Token: $CUST_CSRF" -b $COOKIE_CUST -H "X-User-Role: CUSTOMER" -H "X-User-Id: $CUST_ID" -H "X-User-Name: customer001" -H "X-User-Channel: OA")
AGENT_H=(-H "X-CSRF-Token: $AGENT_CSRF" -b $COOKIE_AGENT -H "X-User-Role: AGENT" -H "X-User-Id: $AGENT_USERNAME" -H "X-User-Name: agent001" -H "X-User-Channel: LOCAL" -H "X-User-Skills: general")

# ============================================================
# Step 3: 获取活跃会话
# ============================================================
echo
echo "[3/10] 获取客户活跃会话"
SESSION_RES=$(curl -s "${CUST_H[@]}" $IM/im/customer/session/active)
SESSION_ID=$(echo $SESSION_RES | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
SESSION_STATUS=$(echo $SESSION_RES | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['status'])")
echo "  ✓ sessionId=$SESSION_ID, status=$SESSION_STATUS"

# ============================================================
# Step 4: 坐席接管会话
# ============================================================
echo
echo "[4/10] 坐席接管会话"
ACCEPT_RES=$(curl -s -X POST "${AGENT_H[@]}" -H "Content-Type: application/json" \
    -d "{\"sessionId\": $SESSION_ID}" $IM/im/agent/session/accept 2>&1)
echo "  $(echo $ACCEPT_RES | head -c 200)"

# ============================================================
# Step 5: 客户和坐席聊天
# ============================================================
echo
echo "[5/10] 双向聊天对话"

chat_pair() {
    local who="$1"
    shift
    local content="$1"
    shift
    local path="$1"
    shift
    local resp=$(curl -s -X POST "$@" -H "Content-Type: application/json" \
        -d "{\"sessionId\": $SESSION_ID, \"content\": \"$content\", \"type\": \"TEXT\"}" \
        $IM$path 2>&1)
    local code=$(echo $resp | python3 -c "import json,sys; d=json.load(sys.stdin) if sys.stdin.read() and '{' in sys.stdin.read() else None; print(d.get('code') if d else 'NO_JSON')" 2>&1 || echo "PARSE_ERR")
    # 重置 stdin
    echo "  [$who] $content → code=$code"
}

chat_pair "CUSTOMER" "你好, 我想买稳健理财" "/im/customer/chat" "${CUST_H[@]}"
sleep 0.5
chat_pair "AGENT"    "您好, 推荐稳健宝, 年化3.8%" "/im/agent/chat" "${AGENT_H[@]}"
sleep 0.5
chat_pair "CUSTOMER" "起购多少?" "/im/customer/chat" "${CUST_H[@]}"
sleep 0.5
chat_pair "AGENT"    "1000元起, 您要做KYC吗?" "/im/agent/chat" "${AGENT_H[@]}"
sleep 0.5
chat_pair "CUSTOMER" "好的, 开始KYC" "/im/customer/chat" "${CUST_H[@]}"

# ============================================================
# Step 6: 客户端上传 5 帧
# ============================================================
echo
echo "[6/10] 模拟客户端定时分片截图 (5 帧)"
for i in 0 1 2 3 4; do
    kind="SCREENSHOT"
    [ $((i % 2)) -eq 1 ] && kind="INTERACTION"
    text="对话阶段 #$i"
    CAPTURE=$(curl -s -X POST "${CUST_H[@]}" -H "Content-Type: application/json" \
        -d "{\"sessionId\": $SESSION_ID, \"frameKind\": \"$kind\", \"imageData\": \"data:image/png;base64,$PNG_DATA\", \"width\": 1280, \"height\": 720, \"durationMs\": 3000, \"metadata\": \"{\\\"seq\\\": $i, \\\"text\\\": \\\"$text\\\", \\\"scrollY\\\": $((i*30))}\"}" \
        $IM/im/replay/capture 2>&1)
    FRAME=$(echo $CAPTURE | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data', {}).get('frameCount', '?'))" 2>&1)
    echo "  帧 $i [$kind]: total=$FRAME"
    sleep 1
done

# ============================================================
# Step 7: 触发合成
# ============================================================
echo
echo "[7/10] 触发 ffmpeg 视频合成"
FINISH_RES=$(curl -s -X POST "${CUST_H[@]}" $IM/im/replay/$SESSION_ID/finish 2>&1)
JOB_ID=$(echo $FINISH_RES | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['jobId'])" 2>&1)
echo "  ✓ jobId = $JOB_ID"

# ============================================================
# Step 8: 轮询
# ============================================================
echo
echo "[8/10] 轮询合成状态 (最多 30 秒)"
VIDEO_URL=""
for i in $(seq 1 15); do
    JOB_RES=$(curl -s "${CUST_H[@]}" $IM/im/replay/$SESSION_ID/job 2>&1)
    STATUS=$(echo $JOB_RES | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('data', {}).get('status', 'NONE') if d.get('data') else 'NONE')" 2>&1)
    echo "  轮询 $i: status = $STATUS"
    if [ "$STATUS" = "SUCCESS" ]; then
        VIDEO_URL=$(echo $JOB_RES | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['videoUrl'])" 2>&1)
        break
    fi
    if [ "$STATUS" = "FAILED" ]; then
        ERR=$(echo $JOB_RES | python3 -c "import json,sys; print(json.load(sys.stdin)['data'].get('errorMessage', '?'))" 2>&1)
        echo "  [FAIL] $ERR"
        exit 1
    fi
    sleep 2
done

# ============================================================
# Step 9: 验证 MP4
# ============================================================
echo
echo "[9/10] 验证 MP4 下载"
echo "  video_url = $VIDEO_URL"
if [ -n "$VIDEO_URL" ] && [ "$VIDEO_URL" != "None" ]; then
    FILE_NAME=$(basename "$VIDEO_URL")
    FULL_URL="$IM/im/replay/video/$FILE_NAME"
    HTTP_CODE=$(curl -s -o /tmp/replay-out.mp4 -w '%{http_code}' "$FULL_URL")
    SIZE=$(stat -c%s /tmp/replay-out.mp4 2>/dev/null || echo 0)
    echo "  HTTP: $HTTP_CODE, 文件: $SIZE bytes"
    file /tmp/replay-out.mp4
    # 尝试 ffprobe
    if command -v ffprobe > /dev/null; then
        DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 /tmp/replay-out.mp4 2>/dev/null)
        CODEC=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 /tmp/replay-out.mp4 2>/dev/null)
        echo "  ✓ ffprobe: 时长=${DUR}s, 编码=$CODEC"
    fi
else
    echo "  [WARN] video_url 未设置"
fi

# ============================================================
# Step 10: DB 验证
# ============================================================
echo
echo "[10/10] DB 验证 (replay_frame / replay_job / chat_session)"
mysql -uroot -proot123 cs_im -e "
SELECT 
    (SELECT COUNT(*) FROM replay_frame WHERE session_id = $SESSION_ID) AS replay_frames,
    (SELECT COUNT(*) FROM replay_job WHERE session_id = $SESSION_ID) AS replay_jobs,
    (SELECT status FROM replay_job WHERE session_id = $SESSION_ID ORDER BY id DESC LIMIT 1) AS job_status,
    (SELECT video_replay_url FROM chat_session WHERE id = $SESSION_ID) AS session_video_url
\G
" 2>&1

echo
echo "============================================================"
echo " ✅ 完整链路端到端模拟测试完成"
echo "============================================================"
echo " sessionId   : $SESSION_ID"
echo " videoUrl    : $VIDEO_URL"
echo " MP4 文件    : /tmp/replay-out.mp4"
echo "============================================================"