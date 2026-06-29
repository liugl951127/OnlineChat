#!/usr/bin/env bash
# =====================================================
# OnlineChat JWT 密钥管理 (v2.2.97)
# =====================================================
#
# 用法:
#   bash scripts/gen-jwt-secret.sh                  # 检查 + 生成
#   bash scripts/gen-jwt-secret.sh --set            # 写入 ~/.bash
#   bash scripts/gen-jwt-secret.sh --print          # 仅打印当前
#
# 优先级:
#   1) $JWT_SECRET 环境变量 (最高)
#   2) ~/.bash 文件 export
#   3) 自动生成 32 字节随机 base64

set -e
SECRET=""

# 1. 先查 ~/.bash (用户偏好)
BASH_FILE="${HOME}/.bash"
if [ -f "${BASH_FILE}" ]; then
    SECRET=$(grep -E '^export[[:space:]]+JWT_SECRET' "${BASH_FILE}" 2>/dev/null | head -1 | sed -E 's/^export[[:space:]]+JWT_SECRET="?([^"]+)"?.*$/\1/')
fi

# 2. 再查 env
if [ -z "${SECRET}" ] && [ -n "${JWT_SECRET}" ]; then
    SECRET="${JWT_SECRET}"
fi

# 3. 还没就生成
if [ -z "${SECRET}" ]; then
    SECRET=$(openssl rand -base64 32 | tr -d '\n')
fi

LEN=${#SECRET}

case "${1:-}" in
    --set)
        if grep -q '^export[[:space:]]+JWT_SECRET=' "${BASH_FILE}" 2>/dev/null; then
            sed -i.bak "s|^export[[:space:]]\+JWT_SECRET=.*|export JWT_SECRET=\"${SECRET}\"|" "${BASH_FILE}"
            echo "✓ updated JWT_SECRET in ${BASH_FILE} (length=${LEN})"
        else
            echo "" >> "${BASH_FILE}"
            echo "# JWT signing secret (v2.2.97)" >> "${BASH_FILE}"
            echo "export JWT_SECRET=\"${SECRET}\"" >> "${BASH_FILE}"
            echo "✓ appended JWT_SECRET into ${BASH_FILE} (length=${LEN})"
        fi
        ;;
    --print)
        if [ -n "${SECRET}" ]; then
            echo "${SECRET}"
        fi
        ;;
    *)
        echo "JWT_SECRET: length=${LEN}"
        if [ "${LEN}" -ge 32 ]; then
            echo "✓ strong (>= 32 bytes)"
            echo "  preview: ${SECRET:0:8}...${SECRET:$(($LEN-8)):8}"
        elif [ "${LEN}" -ge 16 ]; then
            echo "⚠️  medium (16+ bytes)"
        else
            echo "✗ weak (< 16 bytes), regenerate"
        fi
        echo
        echo "用法:"
        echo "  bash scripts/gen-jwt-secret.sh --set    # 写入 ${BASH_FILE}"
        echo "  bash scripts/gen-jwt-secret.sh --print  # 只打印"
        ;;
esac
