#!/usr/bin/env bash
# =====================================================
# OnlineChat 数据库一键重建 (v2.2.54)
# =====================================================
#
# 用法:
#   bash scripts/db-rebuild.sh
#   bash scripts/db-rebuild.sh --skip-seed    # 只建表，不插种子
#   MYSQL_USER=root MYSQL_PASSWORD=xxx bash scripts/db-rebuild.sh
#
# 流程:
#   1. 删除旧数据库 (cs_auth / cs_im / cs_message)
#   2. 重建数据库 + 25 张表
#   3. 验证表数 (cs_auth=1, cs_im=23, cs_message=1)
#   4. (可选) 插种子数据
# =====================================================

set -e

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root123}"

# MySQL 命令路径自动检测
MYSQL_CMD=""
if command -v mysql >/dev/null 2>&1; then
    MYSQL_CMD="mysql"
elif command -v mariadb >/dev/null 2>&1; then
    MYSQL_CMD="mariadb"
else
    echo "❌ 未找到 mysql/mariadb 命令"
    echo "   安装: apt-get install -y mariadb-server"
    exit 1
fi

MYSQL="$MYSQL_CMD -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p$MYSQL_PASSWORD"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INIT_SQL="$ROOT_DIR/docs/db_init_all.sql"
SEED_SQL="$ROOT_DIR/scripts/db_seed.sql"

echo "============================================================"
echo " OnlineChat 数据库重建 (v2.2.54)"
echo "============================================================"
echo "MySQL:   $MYSQL_HOST:$MYSQL_PORT ($MYSQL_CMD)"
echo "Init:    $INIT_SQL"
echo "Seed:    $SEED_SQL"
echo

# ========== 1. 删除旧数据库 ==========
echo "▶ 步骤 1/4: 删除旧数据库"
$MYSQL -e "
  DROP DATABASE IF EXISTS cs_auth;
  DROP DATABASE IF EXISTS cs_im;
  DROP DATABASE IF EXISTS cs_message;
  SELECT 'dropped' AS status;" 2>&1 | tail -3

# ========== 2. 重建数据库 + 表 ==========
echo
echo "▶ 步骤 2/4: 重建数据库 + 25 张表"
if $MYSQL < "$INIT_SQL" 2>&1 | tail -10; then
    echo "✓ SQL 执行成功"
else
    echo "❌ SQL 执行失败"
    exit 1
fi

# ========== 3. 验证表数 ==========
echo
echo "▶ 步骤 3/4: 验证表数"
$MYSQL -e "
  SELECT table_schema, COUNT(*) AS cnt
  FROM information_schema.tables
  WHERE table_schema IN ('cs_auth','cs_im','cs_message')
  GROUP BY table_schema
  ORDER BY table_schema;" 2>&1

EXPECTED="cs_auth=1, cs_im=23, cs_message=1"
echo "  期望: $EXPECTED"

# ========== 4. 种子数据 ==========
if [[ "$1" != "--skip-seed" ]]; then
    echo
    echo "▶ 步骤 4/4: 插种子数据 (10 用户 + 5 会话 + 18 消息 + ...)"
    if $MYSQL < "$SEED_SQL" 2>&1 | tail -10; then
        echo "✓ 种子数据插入成功"

        # 验证种子数
        $MYSQL -e "
          SELECT 'users' AS t, COUNT(*) AS cnt FROM cs_auth.wechat_user
          UNION ALL SELECT 'sessions', COUNT(*) FROM cs_im.chat_session
          UNION ALL SELECT 'messages', COUNT(*) FROM cs_im.chat_message
          UNION ALL SELECT 'faqs', COUNT(*) FROM cs_im.faq
          UNION ALL SELECT 'products', COUNT(*) FROM cs_im.product
          UNION ALL SELECT 'tickets', COUNT(*) FROM cs_im.ticket
          UNION ALL SELECT 'holdings', COUNT(*) FROM cs_im.holding
          UNION ALL SELECT 'offline_msgs', COUNT(*) FROM cs_message.offline_message;" 2>&1
    else
        echo "❌ 种子数据插入失败"
        exit 1
    fi
else
    echo
    echo "⊘ 步骤 4/4: 跳过种子数据 (--skip-seed)"
fi

echo
echo "============================================================"
echo "🎉 数据库重建完成！"
echo "============================================================"
echo
echo "测试账号 (密码统一 pass123, 管理员 admin/admin123):"
$MYSQL -e "
  SELECT customer_id, nickname, role, username
  FROM cs_auth.wechat_user
  ORDER BY role DESC, customer_id;" 2>&1