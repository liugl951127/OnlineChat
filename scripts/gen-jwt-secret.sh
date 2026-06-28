#!/usr/bin/env bash
# =====================================================
# JWT_SECRET 生成脚本 (v2.2.75)
# =====================================================
#
# 生成 48 字节随机字符串, Base64 编码 (输出约 64 字符)
#
# 用法:
#   bash scripts/gen-jwt-secret.sh              # 生成并打印
#   eval $(bash scripts/gen-jwt-secret.sh)     # 自动导出
#   bash scripts/gen-jwt-secret.sh > .env     # 保存到 .env 文件
# =====================================================

set -e

# 优先用 openssl (随机性更好), 兜底用 urandom
if command -v openssl >/dev/null 2>&1; then
    SECRET=$(openssl rand -base64 48 | tr -d '\n')
elif [ -f /dev/urandom ]; then
    SECRET=$(head -c 48 /dev/urandom | base64 | tr -d '\n')
else
    echo "ERROR: 无 openssl 或 /dev/urandom" >&2
    exit 1
fi

echo "JWT_SECRET=$SECRET"
echo "长度: ${#SECRET} 字符"

# 自动检测是否在 eval/source 中调用
if [ "${1:-}" = "--export" ]; then
    # 输出 export 形式 (给 eval 用)
    echo "export JWT_SECRET='$SECRET'"
fi