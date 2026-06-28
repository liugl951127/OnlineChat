#!/usr/bin/env bash
# =====================================================
# YAML 源文件 + jar 包内配置一致性验证 (v2.2.71)
# =====================================================
#
# 检查每个服务:
#   1. application.yml 源码有 spring.config.import
#   2. application-prod.yml 源码有 spring.config.import
#   3. jar 包内 application-prod.yml 有 spring.config.import
#   4. jar 包内 application.yml 有 spring.config.import
#
# 不一致 → 提示需要重新 mvn package
# =====================================================

set -e
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.71 YAML 源文件 + jar 包内配置一致性验证"
echo "============================================================"

for svc in cs-gateway cs-auth cs-im cs-message; do
    echo
    echo "[$svc]"
    
    SRC_MAIN="cs-auth/src/main/resources"  # 修正: 当前是 $svc
    SRC_MAIN="$svc/src/main/resources"
    
    JAR_PATH="$svc/target"
    JAR=$(ls $JAR_PATH/*.jar 2>/dev/null | head -1)
    
    # 1. 源码 application.yml
    if [ -f "$SRC_MAIN/application.yml" ]; then
        if grep -q 'spring.config.import' "$SRC_MAIN/application.yml" 2>/dev/null || \
           grep -q 'config:' "$SRC_MAIN/application.yml" 2>/dev/null && grep -A 2 'config:' "$SRC_MAIN/application.yml" | grep -q 'import:'; then
            ok "源码 application.yml 有 config.import"
        else
            fail "源码 application.yml 缺 config.import"
        fi
    fi
    
    # 2. 源码 application-prod.yml
    if [ -f "$SRC_MAIN/application-prod.yml" ]; then
        if grep -q 'import:' "$SRC_MAIN/application-prod.yml" && \
           grep -B 1 'import:' "$SRC_MAIN/application-prod.yml" | grep -q 'config:'; then
            ok "源码 application-prod.yml 有 config.import"
        else
            fail "源码 application-prod.yml 缺 config.import"
        fi
    fi
    
    # 3. jar 包内 application-prod.yml
    if [ -n "$JAR" ] && [ -f "$JAR" ]; then
        JAR_PROD=$(unzip -p "$JAR" BOOT-INF/classes/application-prod.yml 2>/dev/null || true)
        if [ -n "$JAR_PROD" ]; then
            if echo "$JAR_PROD" | grep -q 'config:' && \
               echo "$JAR_PROD" | grep -A 2 'config:' | grep -q 'import:'; then
                ok "jar 包 application-prod.yml 有 config.import"
            else
                fail "jar 包 application-prod.yml 缺 config.import (需重新 mvn package)"
            fi
        fi
        
        JAR_MAIN=$(unzip -p "$JAR" BOOT-INF/classes/application.yml 2>/dev/null || true)
        if [ -n "$JAR_MAIN" ]; then
            if echo "$JAR_MAIN" | grep -A 2 'config:' | grep -q 'import:'; then
                ok "jar 包 application.yml 有 config.import"
            else
                fail "jar 包 application.yml 缺 config.import (需重新 mvn package)"
            fi
        fi
    else
        echo "  ⚠ $svc jar 包不存在 (需 mvn package)"
    fi
done

echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -gt 0 ]; then
    echo
    echo "❌ 配置不一致! 需要重新编译 jar:"
    echo "  cd /workspace/online-chat"
    echo "  mvn clean package -DskipTests"
    echo
    echo "或者 IDE: Run Maven Build → cs-auth:package"
fi