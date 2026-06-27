#!/bin/bash
# =====================================================
# OnlineChat 一键打包 4 个可执行服务
# =====================================================
set -e
cd "$(dirname "$0")/.."

echo "📦 打包 cs-common (library) ..."
mvn clean install -DskipTests -pl cs-common -am -q

echo ""
echo "📦 打包 4 个微服务 ..."
mvn clean package -DskipTests \
  -pl cs-gateway,cs-auth,cs-im,cs-message \
  -am \
  -q

echo ""
echo "✅ 打包完成："
ls -lh cs-gateway/target/cs-gateway-*.jar \
       cs-auth/target/cs-auth-*.jar \
       cs-im/target/cs-im-*.jar \
       cs-message/target/cs-message-*.jar \
  2>/dev/null | grep -v original | awk '{print "  " $9 " (" $5 ")"}'

echo ""
echo "🚀 启动全部：bash scripts/start-all.sh"