#!/usr/bin/env bash
# =====================================================
# nginx-zhgeliang.conf 一键部署脚本
# =====================================================
set -e

DOMAIN="zhgeliang.com"
CONF_SRC="/workspace/online-chat/docs/nginx-zhgeliang.conf"
CONF_DST="/etc/nginx/conf.d/zhgeliang.conf"

echo "============================================"
echo " 部署 nginx 反向代理: $DOMAIN"
echo "============================================"

# 1. 检查源文件
if [ ! -f "$CONF_SRC" ]; then
    echo "[FAIL] $CONF_SRC 不存在"
    exit 1
fi

# 2. 替换路径占位符 (根据实际部署)
read -p "前端 dist 路径 [/opt/onlinechat/cs-frontend/dist]: " DIST_PATH
DIST_PATH=${DIST_PATH:-/opt/onlinechat/cs-frontend/dist}

# 3. 复制 + 替换路径
cp "$CONF_SRC" "$CONF_DST"
sed -i "s|/opt/onlinechat/cs-frontend/dist|$DIST_PATH|g" "$CONF_DST"

echo "  ✓ 配置已复制到 $CONF_DST"

# 4. 测试语法
nginx -t
echo "  ✓ nginx 语法 OK"

# 5. 重载
nginx -s reload
echo "  ✓ nginx 已重载"

# 6. SSL 证书检查
if [ ! -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]; then
    echo
    echo "[WARN] SSL 证书未找到: /etc/letsencrypt/live/$DOMAIN/fullchain.pem"
    echo "       请先申请证书:"
    echo "         sudo certbot certonly --nginx -d $DOMAIN -d www.$DOMAIN"
fi

# 7. DNS 检查
echo
echo "============================================"
echo " DNS 解析检查:"
host $DOMAIN 2>&1 | head -3 || nslookup $DOMAIN 2>&1 | head -3

# 8. 防火墙提示
echo
echo "============================================"
echo " 防火墙:"
echo "   sudo ufw allow 80/tcp"
echo "   sudo ufw allow 443/tcp"
echo "============================================"

# 9. 测试
echo
echo "============================================"
echo " 部署完成! 测试命令:"
echo "   curl -I https://$DOMAIN/healthz"
echo "   curl https://$DOMAIN/auth/admin/login -X POST -d '{\"username\":\"admin\",\"password\":\"admin123\"}'"
echo "============================================"
