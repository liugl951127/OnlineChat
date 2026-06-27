#!/usr/bin/env bash
# =====================================================
# OnlineChat Nacos + lb:// 路由验证 (v2.2.47)
# =====================================================
#
# 不依赖 nacos 实际运行，仅验证：
#   1. spring.config.import 用 nacos:cs-xxx.yaml
#   2. nacos-discovery + loadbalancer 依赖齐全
#   3. bootstrap.yml 已删除 (用 import 替代)
#   4. @EnableDiscoveryClient 注解
#   5. routes 用 lb:// 模式
# =====================================================

set -e
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.47 Nacos 注册 + lb:// 路由配置验证"
echo "============================================================"

# ========== 1. spring.config.import (替代 bootstrap.yml) ==========
echo
echo "[1/7] 检查 spring.config.import (Nacos 远程配置)"
for svc in cs-gateway cs-auth cs-im cs-message; do
    YML="$svc/src/main/resources/application.yml"
    if grep -q "spring.config.import" "$YML" 2>/dev/null; then
        IMPORT_TARGET=$(grep -A 5 "config:" "$YML" | grep -E "optional:nacos:" | head -1 | tr -d ' ')
        if [ -n "$IMPORT_TARGET" ]; then
            ok "$svc spring.config.import: $IMPORT_TARGET"
        else
            fail "$svc spring.config.import 缺 nacos:"
        fi
    else
        fail "$svc application.yml 缺 spring.config.import"
    fi
done

# ========== 2. bootstrap.yml 应已删除 ==========
echo
echo "[2/7] 检查 bootstrap.yml (应已删除)"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if [ -f "$svc/src/main/resources/bootstrap.yml" ]; then
        fail "$svc bootstrap.yml 仍存在 (v2.2.47 已弃用)"
    else
        ok "$svc bootstrap.yml 已删除 (用 import 替代)"
    fi
done

# ========== 3. nacos discovery 配置 ==========
echo
echo "[3/7] 检查 nacos discovery 配置"
for svc in cs-gateway cs-auth cs-im cs-message; do
    YML="$svc/src/main/resources/application.yml"
    # YAML 嵌套需匹配具体字段名 nacos: + discovery:
    if grep -E "^\s+nacos:|^\s+discovery:" "$YML" >/dev/null; then
        ok "$svc application.yml 有 nacos.discovery"
    else
        fail "$svc application.yml 缺 nacos.discovery"
    fi
    # 启用注册
    if grep -q "register-enabled" "$YML"; then
        ok "$svc nacos.register-enabled 配置"
    else
        fail "$svc nacos.register-enabled 缺配置"
    fi
    # 心跳
    if grep -q "heartbeat-interval" "$YML"; then
        ok "$svc nacos.heartbeat-interval 配置"
    else
        fail "$svc nacos.heartbeat-interval 缺配置"
    fi
done

# ========== 4. nacos + loadbalancer 依赖 ==========
echo
echo "[4/7] 检查 nacos-discovery + loadbalancer 依赖"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if grep -q "spring-cloud-starter-alibaba-nacos-discovery" "$svc/pom.xml"; then
        ok "$svc pom 有 nacos-discovery"
    else
        fail "$svc pom 缺 nacos-discovery"
    fi
    if grep -q "spring-cloud-starter-loadbalancer" "$svc/pom.xml"; then
        ok "$svc pom 有 loadbalancer (lb:// 路由关键依赖)"
    else
        fail "$svc pom 缺 loadbalancer"
    fi
done

# ========== 5. 不应再需要 bootstrap 启动器 ==========
echo
echo "[5/7] 检查 spring-cloud-starter-bootstrap (应已删除)"
for svc in cs-gateway cs-auth cs-im cs-message; do
    if grep -q "spring-cloud-starter-bootstrap" "$svc/pom.xml"; then
        fail "$svc pom 仍含 spring-cloud-starter-bootstrap (v2.2.47 已用 import 替代)"
    else
        ok "$svc pom 已移除 bootstrap starter"
    fi
done

# ========== 6. @EnableDiscoveryClient 注解 ==========
echo
echo "[6/7] 检查 @EnableDiscoveryClient 注解"
for svc in cs-gateway cs-auth cs-im cs-message; do
    APP_FILE=$(find "$svc/src/main/java" -name "*Application.java" | head -1)
    if grep -q "@EnableDiscoveryClient" "$APP_FILE"; then
        ok "$svc $(basename $APP_FILE) 有 @EnableDiscoveryClient"
    else
        fail "$svc $(basename $APP_FILE) 缺 @EnableDiscoveryClient"
    fi
done

# ========== 7. cs-gateway lb:// 路由 ==========
echo
echo "[7/7] 检查 cs-gateway lb:// 路由"
GATEWAY_YML="cs-gateway/src/main/resources/application.yml"
for svc_name in cs-auth cs-robot cs-im cs-trade cs-message; do
    if grep -q "uri: lb://$svc_name" "$GATEWAY_YML"; then
        ok "cs-gateway 路由 → lb://$svc_name"
    else
        fail "cs-gateway 缺 lb://$svc_name 路由"
    fi
done

# 检查 discovery locator
if grep -q "discovery.locator" "$GATEWAY_YML"; then
    ok "cs-gateway discovery.locator 启用 (自动 /serviceId/** 路由)"
else
    echo "  ℹ cs-gateway discovery.locator 未启用 (用静态 routes)"
fi

# ========== 汇总 ==========
echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -eq 0 ]; then
    echo
    echo "🎉 nacos + lb:// 路由配置验证通过！"
    echo
    echo " 启用流程:"
    echo "  1. 启动 nacos:"
    echo "     docker run -d -p 8848:8848 -p 9848:9848 --name nacos nacos/nacos-server:v2.3.2"
    echo
    echo "  2. 启动 4 服务 (默认 NACOS_DISCOVERY_ENABLED=true 已开启):"
    echo "     export NACOS_ADDR=127.0.0.1:8848"
    echo "     bash scripts/deploy.sh"
    echo
    echo "  3. gateway 自动通过 lb://cs-auth 调用 nacos 注册的服务:"
    echo "     路由: /auth/** → lb://cs-auth → nacos → cs-auth:9001"
    echo
    echo "  4. nacos 控制台验证:"
    echo "     http://127.0.0.1:8848/nacos (nacos/nacos)"
    echo "     → 服务管理 → 服务列表 → 4 个服务都注册"
    echo
    echo "  5. nacos 远程配置 (可选):"
    echo "     nacos 控制台 → 配置管理 → 配置列表 → 新建:"
    echo "       Data ID: cs-gateway.yaml"
    echo "       Group: DEFAULT_GROUP"
    echo "       Format: YAML"
    echo "       配置内容: spring.cloud.gateway.routes[0].uri=lb://cs-auth"
    echo "     → 应用通过 spring.config.import 自动拉取"
fi

exit $FAIL