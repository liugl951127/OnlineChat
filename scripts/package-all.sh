#!/bin/bash
# =====================================================
# OnlineChat 一键打包 4 个生产可执行 jar（默认 prod profile）
#
# 输出:
#   cs-gateway/target/cs-gateway-1.7.1.jar
#   cs-auth/target/cs-auth-1.7.1.jar
#   cs-im/target/cs-im-1.7.1.jar
#   cs-message/target/cs-message-1.7.1.jar
#
# 用法: bash scripts/package-all.sh
# =====================================================
set -e
cd "$(dirname "$0")/.."

echo "📦 打包 cs-common (library) ..."
mvn clean install -DskipTests -pl cs-common -am -q

echo ""
echo "📦 打包 4 个微服务 (prod profile 默认) ..."
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
echo "🚀 nohup 启动示例（4 个终端分别执行）:"
echo ""
echo "  nohup java -jar cs-gateway/target/cs-gateway-1.7.1.jar \\"
echo "    --spring.profiles.active=prod > /var/log/onlinechat/gateway.log 2>&1 &"
echo ""
echo "  nohup java -jar cs-auth/target/cs-auth-1.7.1.jar \\"
echo "    --spring.profiles.active=prod > /var/log/onlinechat/auth.log 2>&1 &"
echo ""
echo "  nohup java -jar cs-im/target/cs-im-1.7.1.jar \\"
echo "    --spring.profiles.active=prod > /var/log/onlinechat/im.log 2>&1 &"
echo ""
echo "  nohup java -jar cs-message/target/cs-message-1.7.1.jar \\"
echo "    --spring.profiles.active=prod > /var/log/onlinechat/message.log 2>&1 &"