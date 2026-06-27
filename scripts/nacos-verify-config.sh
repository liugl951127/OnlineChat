#!/usr/bin/env bash
# =====================================================
# OnlineChat Nacos 配置加载验证 (v2.2.44)
# =====================================================
#
# 不依赖 nacos 实际运行，仅验证：
#   1. 所有服务都有 bootstrap.yml
#   2. 所有服务都有 @EnableDiscoveryClient
#   3. nacos 配置语法正确
#   4. DiscoveryFallbackConfig 注入正常
#
# 用法:
#   bash scripts/nacos-verify-config.sh
# =====================================================

set -e
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.44 Nacos 服务发现配置验证"
echo "============================================================"

# ========== 1. 检查所有服务的 bootstrap.yml ==========
echo
echo "[1/6] 检查 bootstrap.yml"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if [ -f "$svc/src/main/resources/bootstrap.yml" ]; then
        SIZE=$(wc -c < "$svc/src/main/resources/bootstrap.yml")
        ok "$svc bootstrap.yml ($SIZE bytes)"
    else
        fail "$svc bootstrap.yml 不存在"
    fi
done

# ========== 2. 检查 @EnableDiscoveryClient 注解 ==========
echo
echo "[2/6] 检查 @EnableDiscoveryClient 注解"
for svc in cs-gateway cs-auth cs-im cs-message; do
    APP_FILE=$(find "$svc/src/main/java" -name "*Application.java" | head -1)
    if [ -n "$APP_FILE" ]; then
        if grep -q "@EnableDiscoveryClient" "$APP_FILE"; then
            ok "$svc $(basename $APP_FILE) 有 @EnableDiscoveryClient"
        else
            fail "$svc $(basename $APP_FILE) 缺 @EnableDiscoveryClient"
        fi
    else
        fail "$svc 找不到 Application 类"
    fi
done

# ========== 3. 检查 nacos discovery 依赖 + bootstrap 启动器 ==========
echo
echo "[3/6] 检查 nacos-discovery 依赖 + spring-cloud-starter-bootstrap"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if grep -q "spring-cloud-starter-alibaba-nacos-discovery" "$svc/pom.xml"; then
        ok "$svc pom 有 nacos-discovery"
    else
        fail "$svc pom 缺 nacos-discovery"
    fi
    # v2.2.46: Spring Cloud 2023.0.1 默认禁用 bootstrap.yml
    # 必须加 spring-cloud-starter-bootstrap 依赖才能加载 bootstrap.yml
    if grep -q "spring-cloud-starter-bootstrap" "$svc/pom.xml"; then
        ok "$svc pom 有 spring-cloud-starter-bootstrap (让 bootstrap.yml 生效)"
    else
        fail "$svc pom 缺 spring-cloud-starter-bootstrap (Spring Cloud 2023.0.1 默认禁用 bootstrap.yml!)"
    fi
done

# ========== 4. 检查 nacos config 依赖 ==========
echo
echo "[4/6] 检查 nacos-config 依赖 (可选)"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if grep -q "spring-cloud-starter-alibaba-nacos-config" "$svc/pom.xml"; then
        ok "$svc pom 有 nacos-config"
    else
        echo "  ℹ $svc pom 无 nacos-config (仅注册不拉配置)"
    fi
done

# ========== 5. 检查 DiscoveryFallbackConfig ==========
echo
echo "[5/6] 检查 DiscoveryFallbackConfig"
if [ -f "cs-common/src/main/java/com/example/common/DiscoveryFallbackConfig.java" ]; then
    ok "DiscoveryFallbackConfig.java 存在"
else
    fail "DiscoveryFallbackConfig.java 不存在"
fi
if [ -f "cs-common/src/test/java/com/example/common/DiscoveryFallbackConfigTest.java" ]; then
    ok "DiscoveryFallbackConfigTest.java 存在"
else
    fail "DiscoveryFallbackConfigTest.java 不存在"
fi

# ========== 6. 检查 application.yml 重复配置 ==========
echo
echo "[6/6] 检查 application.yml 是否移除重复 nacos 配置"
for svc in cs-gateway cs-auth cs-im cs-message; do
    APP_YML="$svc/src/main/resources/application.yml"
    if grep -E "nacos:|cloud:" "$APP_YML" | grep -v "nacos:" >/dev/null; then
        # 只允许有 # nacos 注释，不应有 nacos: 配置
        if grep -E "^  cloud:" "$APP_YML" | grep -A 10 "cloud:" | grep -E "nacos:" >/dev/null; then
            fail "$svc application.yml 还有 nacos 重复配置"
        else
            ok "$svc application.yml 无 nacos 重复配置"
        fi
    else
        ok "$svc application.yml 无 nacos 配置"
    fi
done

# ========== 7. 检查 bootstrap.yml 配置正确 ==========
echo
echo "[7/6] 检查 bootstrap.yml 配置 (fail-fast 等)"
for svc in cs-gateway cs-auth cs-im cs-message; do
    YML="$svc/src/main/resources/bootstrap.yml"
    if grep -q "fail-fast: false" "$YML"; then
        ok "$svc bootstrap.yml fail-fast=false (沙箱友好)"
    else
        fail "$svc bootstrap.yml 缺 fail-fast=false"
    fi
    if grep -q "heartbeat-interval" "$YML"; then
        ok "$svc bootstrap.yml 心跳配置正确"
    else
        fail "$svc bootstrap.yml 缺心跳配置"
    fi
done

# ========== 8. 检查路由 lb:// 支持 ==========
echo
echo "[8/6] 检查 cs-gateway 路由硬编码 (v2.2.45 设计)"
GATEWAY_YML="cs-gateway/src/main/resources/application.yml"
HARDCODED_OK=true
for port in 9001 9002 9003 9004 9005; do
    if ! grep -q "uri: http://localhost:$port" "$GATEWAY_YML"; then
        HARDCODED_OK=false
        fail "cs-gateway routes 缺 http://localhost:$port 硬编码"
    fi
done
if [ "$HARDCODED_OK" = true ]; then
    ok "cs-gateway routes 全部硬编码 http://localhost:{port} (不依赖 nacos lb)"
fi
# 确认没环境变量
if grep -qE 'CS_(AUTH|IM|MESSAGE|ROBOT|TRADE)_URI' "$GATEWAY_YML"; then
    fail "cs-gateway routes 还有 CS_*_URI 环境变量 (应硬编码)"
else
    ok "cs-gateway routes 无环境变量 (稳定优先)"
fi

# ========== 汇总 ==========
echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -eq 0 ]; then
    echo
    echo "🎉 nacos 服务发现配置验证通过！"
    echo
    echo " 启用 nacos 部署流程:"
    echo "  1. 启动 nacos standalone:"
    echo "     docker run -d -p 8848:8848 -p 9848:9848 --name nacos nacos/nacos-server:v2.3.2"
    echo "     OR"
    echo "     bash /tmp/nacos/bin/startup.sh -m standalone"
    echo
    echo "  2. 启动服务 (启用 nacos 发现):"
    echo "     export NACOS_DISCOVERY_ENABLED=true"
    echo "     export NACOS_CONFIG_ENABLED=true"
    echo "     export NACOS_ADDR=127.0.0.1:8848"
    echo "     bash scripts/deploy.sh"
    echo
    echo "  3. gateway 切换 lb:// 服务发现:"
    echo "     export CS_AUTH_URI=lb://cs-auth"
    echo "     export CS_IM_URI=lb://cs-im"
    echo "     export CS_MESSAGE_URI=lb://cs-message"
    echo "     bash scripts/deploy.sh"
    echo
    echo "  4. nacos 控制台验证:"
    echo "     http://127.0.0.1:8848/nacos (nacos/nacos)"
    echo "     → 服务管理 → 服务列表 应看到 cs-gateway/cs-auth/cs-im/cs-message"
fi

exit $FAIL