#!/usr/bin/env bash
# =====================================================
# OnlineChat 一键 Maven 部署脚本 (v2.2.33)
# =====================================================
#
# 功能:
#   1. 环境检查 (mvn / java / 端口)
#   2. 清理旧进程 + 旧日志
#   3. Maven 打包 4 个微服务 (prod profile, mock=false)
#   4. nohup 启动 (后台, --spring.profiles.active=prod)
#   5. 等待 actuator/health UP
#   6. 端到端 OAuth 验证
#
# 用法:
#   bash scripts/deploy.sh                    # 完整流程: 清理 + 打包 + 启动 + 验证
#   bash scripts/deploy.sh --skip-build       # 跳过打包 (用现成的 jar)
#   bash scripts/deploy.sh --skip-start       # 只打包不启动
#   bash scripts/deploy.sh --skip-verify      # 跳过自检
#   bash scripts/deploy.sh --dev              # 用 --spring.profiles.active=dev
#   bash scripts/deploy.sh --mock             # 用 mock=true (沙箱无真实 appId)
#   bash scripts/deploy.sh --stop             # 只停服务
#   bash scripts/deploy.sh --clean            # 只清理 (停服务 + 删 jar + 删日志)
#
# 环境变量覆盖:
#   JAVA_OPTS     JVM 参数 (默认 "-Xms512m -Xmx1024m")
#   LOG_DIR       日志目录 (默认 /var/log/onlinechat)
#   MOCK          WECHAT_OA_MOCK 默认值 (默认 false)
#   NACOS         NACOS_DISCOVERY_ENABLED (默认 false)
#
# =====================================================
set -e

# ---------------- 配置 ----------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

VERSION="1.7.1"
APP_PREFIX="onlinechat"
LOG_DIR="${LOG_DIR:-/var/log/onlinechat}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
HEALTH_TIMEOUT=120       # 等 actuator/health UP 的总超时秒数
HEALTH_INTERVAL=3        # 轮询间隔秒数
MOCK="${MOCK:-false}"    # 默认 mock=false (真实微信)
NACOS_DISCOVERY_ENABLED="${NACOS:-false}"  # 默认关 nacos
NACOS_CONFIG_ENABLED="${NACOS:-false}"
PROFILE="prod"

# 微服务定义: name|port|jar_path|main_module|extra_args
SERVICES=(
    "cs-gateway|9000|cs-gateway/target/cs-gateway-${VERSION}.jar|cs-gateway|"
    "cs-auth|9001|cs-auth/target/cs-auth-${VERSION}.jar|cs-auth|"
    "cs-im|9003|cs-im/target/cs-im-${VERSION}.jar|cs-im|-Djava.library.path=/tmp/jni/org/bytedeco/opencv/org/bytedeco/opencv/linux-x86_64"
    "cs-message|9005|cs-message/target/cs-message-${VERSION}.jar|cs-message|"
)

# ---------------- 颜色 ----------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0

ok()   { echo -e "  ${GREEN}✓${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC} $1"; FAIL=$((FAIL+1)); }
warn() { echo -e "  ${YELLOW}!${NC} $1"; }
info() { echo -e "  ${CYAN}→${NC} $1"; }
hdr()  { echo -e "\n${BLUE}==>${NC} ${YELLOW}$1${NC}"; }

# ---------------- 参数解析 ----------------
SKIP_BUILD=0
SKIP_START=0
SKIP_VERIFY=0
STOP_ONLY=0
CLEAN_ONLY=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-build)  SKIP_BUILD=1; shift ;;
        --skip-start)  SKIP_START=1; shift ;;
        --skip-verify) SKIP_VERIFY=1; shift ;;
        --dev)         PROFILE="dev"; shift ;;
        --mock)        MOCK="true"; shift ;;
        --stop)        STOP_ONLY=1; shift ;;
        --clean)       CLEAN_ONLY=1; shift ;;
        --help|-h)
            grep '^#' "$0" | head -30 | sed 's/^# \?//'
            exit 0 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ---------------- Banner ----------------
cat <<'EOF'
  ____                  _       _    ____ _           _
 / ___|___  _ __ ___  __| | ___ | |_ / ___| |__   ___ | |_
| |   / _ \| '__/ _ \/ _` |/ _ \| __| |   | '_ \ / _ \| __|
| |__| (_) | | |  __/ (_| | (_) | |_| |___| | | | (_) | |_
 \____\___/|_|  \___|\__,_|\___/ \__|\____|_| |_|\___/ \__|

EOF
echo "  部署脚本 v2.2.33"
echo "  profile: $PROFILE  |  mock: $MOCK  |  nacos: $NACOS_DISCOVERY_ENABLED"
echo "  log dir: $LOG_DIR"
echo ""

# ---------------- 1. 环境检查 ----------------
hdr "1/6 环境检查"

check_cmd() {
    if command -v "$1" >/dev/null 2>&1; then
        local v=$($1 --version 2>&1 | head -1)
        ok "$1: $v"
    else
        fail "$1 未安装"
        return 1
    fi
}

check_cmd java || exit 1
check_cmd mvn || { fail "mvn 未安装"; exit 1; }
check_cmd curl >/dev/null 2>&1 || warn "curl 未安装（自检需要）"

# 检查 JDK 版本
JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')
case "$JAVA_VER" in
    17*|21*|11*) ok "JDK $JAVA_VER" ;;
    *) warn "JDK $JAVA_VER (建议 17 或 21)" ;;
esac

# 检查端口
for entry in "${SERVICES[@]}"; do
    IFS='|' read -r name port _ _ _ <<<"$entry"
    if ss -tln 2>/dev/null | grep -q ":$port "; then
        warn "端口 $port ($name) 已被占用"
    fi
done

# 检查内存（生产建议 4G+）
MEM_TOTAL=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print int($2/1024/1024)}')
if [ -n "$MEM_TOTAL" ]; then
    if [ "$MEM_TOTAL" -ge 4 ]; then
        ok "系统内存 ${MEM_TOTAL}G"
    else
        warn "系统内存 ${MEM_TOTAL}G（建议 ≥4G）"
    fi
fi

# ---------------- 2. 清理旧进程 ----------------
cleanup_processes() {
    hdr "清理旧进程"
    for entry in "${SERVICES[@]}"; do
        IFS='|' read -r name port _ _ _ <<<"$entry"
        PIDS=$(pgrep -f "cs-$name" 2>/dev/null || true)
        if [ -n "$PIDS" ]; then
            for pid in $PIDS; do
                kill -9 "$pid" 2>/dev/null && info "killed $name PID=$pid" || true
            done
        else
            info "$name 未运行"
        fi
    done
    sleep 2
}

# ---------------- 3. Maven 打包 ----------------
do_build() {
    hdr "Maven 打包 (prod profile)"

    if [ ! -d "cs-common" ]; then
        fail "未找到 cs-common 目录，请 cd 到 online-chat 根目录"
        exit 1
    fi

    info "清理 + 编译 cs-common ..."
    mvn clean install -DskipTests -pl cs-common -am -q 2>&1 | tail -5 || { fail "cs-common 编译失败"; exit 1; }
    ok "cs-common 编译完成"

    info "打包 4 个微服务 ..."
    mvn clean package -DskipTests \
        -pl cs-gateway,cs-auth,cs-im,cs-message \
        -am -q 2>&1 | tail -5 || { fail "微服务打包失败"; exit 1; }
    ok "4 个微服务打包完成"

    echo ""
    info "生成的 jar:"
    for entry in "${SERVICES[@]}"; do
        IFS='|' read -r name port jar _ _ <<<"$entry"
        if [ -f "$jar" ]; then
            SIZE=$(ls -lh "$jar" | awk '{print $5}')
            ok "$jar ($SIZE)"
        else
            fail "$jar 不存在"
            exit 1
        fi
    done
}

# ---------------- 4. nohup 启动 ----------------
do_start() {
    hdr "nohup 启动 4 个微服务"
    mkdir -p "$LOG_DIR"

    for entry in "${SERVICES[@]}"; do
        IFS='|' read -r name port jar module extra <<<"$entry"
        if [ ! -f "$jar" ]; then
            fail "$jar 不存在，跳过 $name"
            continue
        fi

        info "启动 $name (port=$port, log=$LOG_DIR/$name.log) ..."

        # 用 setsid 避免 sandbox 杀进程
        setsid nohup java $JAVA_OPTS $extra \
            -jar "$jar" \
            --spring.profiles.active="$PROFILE" \
            --wechat.oa.mock="$MOCK" \
            --wechat.mini.mock="$MOCK" \
            --wechat.work.mock="$MOCK" \
            --spring.cloud.nacos.discovery.enabled="$NACOS_DISCOVERY_ENABLED" \
            --spring.cloud.nacos.config.enabled="$NACOS_CONFIG_ENABLED" \
            > "$LOG_DIR/$name.log" 2>&1 < /dev/null &
        disown -a 2>/dev/null || true
        ok "$name 已启动"
    done

    echo ""
    info "等待 $HEALTH_TIMEOUT 秒，actuator/health UP ..."
    for entry in "${SERVICES[@]}"; do
        IFS='|' read -r name port _ _ _ <<<"$entry"
        URL="http://127.0.0.1:$port/actuator/health"
        UP=0
        for ((i=1; i<=HEALTH_TIMEOUT; i+=HEALTH_INTERVAL)); do
            if curl -sf --max-time 2 "$URL" 2>/dev/null | grep -q '"status":"UP"'; then
                ok "$name UP (port $port, ${i}s)"
                UP=1
                break
            fi
            sleep $HEALTH_INTERVAL
        done
        [ "$UP" = "0" ] && fail "$name 未 UP (port $port)，查看 $LOG_DIR/$name.log"
    done
}

# ---------------- 5. 端到端自检 ----------------
do_verify() {
    hdr "端到端 OAuth 自检"

    # 5.1 /authorize-json 返回 URL
    info "5.1 GET /auth/wechat-oa/authorize-json"
    RESP=$(curl -s --max-time 5 http://127.0.0.1:9001/auth/wechat-oa/authorize-json)
    if echo "$RESP" | grep -q '"url":'; then
        ok "返回 url 字段"
    else
        fail "无 url 字段: $RESP"
    fi

    # 5.2 mock=false 时 url 应是真实微信
    if [ "$MOCK" = "false" ]; then
        if echo "$RESP" | grep -q 'open.weixin.qq.com'; then
            ok "真实微信授权 URL"
        else
            fail "url 不是 open.weixin.qq.com: $RESP"
        fi
    fi

    # 5.3 mock=true 时 url 应是 MOCK-xxx
    if [ "$MOCK" = "true" ]; then
        if echo "$RESP" | grep -q 'MOCK-\|mock=true'; then
            ok "Mock 模式 URL"
        else
            fail "Mock 模式 url 不对: $RESP"
        fi
    fi

    # 5.4 /callback-json 返回 token
    info "5.2 GET /auth/wechat-oa/callback-json?code=..."
    RESP=$(curl -s --max-time 5 "http://127.0.0.1:9001/auth/wechat-oa/callback-json?code=DEPLOY-TEST")
    if echo "$RESP" | grep -q '"token":'; then
        ok "callback-json 返回 token"
    else
        fail "callback-json 无 token: $RESP"
    fi

    # 5.5 Gateway → cs-auth 完整链路
    if curl -sf --max-time 2 http://127.0.0.1:9000/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        info "5.3 Gateway → cs-auth"
        RESP=$(curl -s --max-time 5 "http://127.0.0.1:9000/auth/wechat-oa/authorize-json")
        if echo "$RESP" | grep -q '"url":'; then
            ok "Gateway → cs-auth 链路通"
        else
            fail "Gateway 路由不通: $RESP"
        fi
    else
        warn "cs-gateway 未 UP，跳过 Gateway 验证"
    fi
}

# ---------------- 6. 汇总 ----------------
summary() {
    hdr "部署汇总"
    echo ""
    echo "  📦 状态:    $PASS 通过 / $FAIL 失败"
    echo "  🚀 服务:    ${SERVICES[*]}"
    echo "  📋 日志:    $LOG_DIR/{gateway,auth,im,message}.log"
    echo "  🔧 Profile: $PROFILE"
    echo "  🎭 Mock:    $MOCK"
    echo "  📡 Nacos:   discovery=$NACOS_DISCOVERY_ENABLED config=$NACOS_CONFIG_ENABLED"
    echo ""
    if [ $FAIL -eq 0 ]; then
        echo -e "  ${GREEN}🎉 部署完成！${NC}"
        echo ""
        echo "  验证命令:"
        echo "    curl http://127.0.0.1:9000/actuator/health  # Gateway"
        echo "    curl http://127.0.0.1:9001/actuator/health  # Auth"
        echo "    bash scripts/e2e-wechat-oauth.sh            # 完整 OAuth 自检"
    else
        echo -e "  ${RED}❌ $FAIL 项失败${NC}"
        echo ""
        echo "  排查:"
        echo "    tail -50 $LOG_DIR/gateway.log"
        echo "    tail -50 $LOG_DIR/auth.log"
        exit 1
    fi
}

# ---------------- 主流程 ----------------
case 1 in
1)
    if [ "$CLEAN_ONLY" = "1" ]; then
        cleanup_processes
        rm -rf "$LOG_DIR" 2>/dev/null || true
        rm -f cs-*/target/cs-*-${VERSION}.jar 2>/dev/null || true
        hdr "清理完成"
        exit 0
    fi
    if [ "$STOP_ONLY" = "1" ]; then
        cleanup_processes
        hdr "停止完成"
        exit 0
    fi

    if [ "$SKIP_BUILD" = "0" ]; then
        do_build
    fi

    if [ "$SKIP_START" = "0" ]; then
        cleanup_processes
        do_start
    fi

    if [ "$SKIP_VERIFY" = "0" ]; then
        do_verify
    fi

    summary
    ;;
esac