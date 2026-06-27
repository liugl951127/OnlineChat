#!/bin/bash
# =====================================================
# 从 domain Java 类自动生成 CREATE TABLE SQL
# 用法：bash scripts/gen_sql_from_domain.sh
# =====================================================
set -e
cd "$(dirname "$0")/.."

# 转换 camelCase → snake_case
to_snake() {
  echo "$1" | sed 's/\([A-Z]\)/_\L\1/g' | sed 's/^_//'
}

# 单字段 → SQL 片段
field_to_sql() {
  local f=$1
  local type=$2
  local fill=$3

  case "$type" in
    Long|Integer|Int) sql_type="BIGINT" ;;
    String) sql_type="VARCHAR(255)" ;;
    LocalDateTime|Date) sql_type="DATETIME" ;;
    Boolean|boolean) sql_type="TINYINT(1)" ;;
    Double|double|Float|float) sql_type="DECIMAL(18,4)" ;;
    byte\[\]|Byte\[\]) sql_type="BLOB" ;;
    *) sql_type="VARCHAR(255)" ;;
  esac

  # 默认值（基于 fill 注解）
  local default=""
  case "$fill" in
    INSERT|INSERT_UPDATE) default=" NOT NULL DEFAULT CURRENT_TIMESTAMP" ;;
  esac

  # 枚举判断
  case "$f" in
    id) sql_type="BIGINT"; default=" NOT NULL AUTO_INCREMENT PRIMARY KEY" ;;
  esac

  echo "    $f $sql_type$default,"
}

# 解析单个 Java 文件 → CREATE TABLE
java_to_create() {
  local file=$1
  local cls=$(basename $file .java)
  local table=$(grep -E "@TableName\(" $file | head -1 | sed -E 's/.*\("([^"]+)".*/\1/')
  if [ -z "$table" ]; then
    echo "  ⚠️ 跳过：$cls 无 @TableName"
    return
  fi

  echo ""
  echo "-- $cls → $table"
  echo "CREATE TABLE IF NOT EXISTS $table ("
  echo "    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"

  grep -E "^    private " $file | grep -v "static\|final" | while read line; do
    local name=$(echo "$line" | sed -E 's/.*private ([A-Za-z<>0-9]+) +([a-zA-Z]+).*/\2/')
    local type=$(echo "$line" | sed -E 's/.*private ([A-Za-z<>0-9]+) +([a-zA-Z]+).*/\1/')
    local fill=""
    # 检查上面是否有 FieldFill 注解
    if grep -B 1 "private $type $name;" $file | grep -q "FieldFill.INSERT_UPDATE"; then
      fill="INSERT_UPDATE"
    elif grep -B 1 "private $type $name;" $file | grep -q "FieldFill.INSERT"; then
      fill="INSERT"
    fi
    [ "$name" = "id" ] && continue
    field_to_sql "$name" "$type" "$fill"
  done

  echo "    deleted         TINYINT(1) NOT NULL DEFAULT 0"
  echo ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='$cls';"
}

# ===== 主流程 =====
{
  echo "-- ====================================================="
  echo "-- 自动生成于 $(date '+%Y-%m-%d %H:%M:%S')"
  echo "-- 数据库：cs_auth + cs_im（合并自所有 domain 类）"
  echo "-- ====================================================="
  echo ""
  echo "CREATE DATABASE IF NOT EXISTS cs_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  echo "CREATE DATABASE IF NOT EXISTS cs_im   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  echo ""
  echo "USE cs_auth;"

  for f in cs-auth/src/main/java/com/example/auth/domain/*.java; do
    java_to_create $f
  done

  echo ""
  echo "USE cs_im;"

  for f in cs-im/src/main/java/com/example/im/domain/*.java; do
    java_to_create $f
  done
} > /tmp/gen.sql

cat /tmp/gen.sql