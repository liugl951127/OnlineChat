#!/usr/bin/env python3
"""从 domain Java 类自动生成统一 db_init_all.sql"""
import re
import os
import sys
from pathlib import Path

ROOT = Path("/workspace/online-chat")
SQL_FILE = ROOT / "docs/db_init_all.sql"

# 类型映射
TYPE_MAP = {
    "Long": "BIGINT",
    "Integer": "INT",
    "int": "INT",
    "String": "VARCHAR(255)",
    "LocalDateTime": "DATETIME",
    "LocalDate": "DATE",
    "LocalTime": "TIME",
    "Date": "DATETIME",
    "Boolean": "TINYINT(1)",
    "boolean": "TINYINT(1)",
    "Double": "DECIMAL(18,4)",
    "double": "DECIMAL(18,4)",
    "Float": "DECIMAL(18,4)",
    "float": "DECIMAL(18,4)",
    "BigDecimal": "DECIMAL(18,4)",
    "byte[]": "BLOB",
    "Byte[]": "BLOB",
}


def snake(name):
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1_\2', name)
    s = re.sub(r'([a-z\d])([A-Z])', r'\1_\2', s)
    return s.lower()


def parse_domain(java_file):
    """解析一个 domain 类，返回 (table_name, comment, fields, indexes)"""
    with open(java_file) as f:
        text = f.read()

    # @TableName
    m = re.search(r'@TableName\("([^"]+)"\)', text)
    if not m:
        return None
    table = m.group(1)

    # class 注释（用类名作为注释）
    cls = java_file.stem

    # 解析字段
    fields = []
    # 匹配所有 private 字段
    # 先 split by annotation groups
    field_pattern = re.compile(
        r'(?:@\w+(?:\([^)]*\))?\s*)*'  # 注解
        r'private\s+(\S+(?:<[^>]+>)?)\s+(\w+);',  # 字段声明
        re.MULTILINE
    )

    # 单独处理 FieldFill
    fill_lines = text.split('\n')
    # 创建 field -> fill 映射
    fill_map = {}
    for i, line in enumerate(fill_lines):
        m_fill = re.search(r'FieldFill\.(\w+)', line)
        if m_fill:
            # 下一个非空行应该是 private 字段
            for j in range(i + 1, min(i + 4, len(fill_lines))):
                m_field = re.search(r'private\s+\S+(?:<[^>]+>)?\s+(\w+);', fill_lines[j])
                if m_field:
                    fill_map[m_field.group(1)] = m_fill.group(1)
                    break

    # 是否 @TableLogic
    has_logic_delete = '@TableLogic' in text

    for m in field_pattern.finditer(text):
        type_ = m.group(1).strip()
        name = m.group(2)
        if name == 'serialVersionUID':
            continue

        snake_name = snake(name)

        # 类型转换
        base_type = type_.split('<')[0]
        sql_type = TYPE_MAP.get(base_type, "VARCHAR(255)")
        if 'String' == base_type:
            # 根据名字猜长度
            if any(k in name.lower() for k in ['no', 'code', 'id', 'type', 'status', 'role', 'level', 'channel', 'name', 'category']):
                sql_type = "VARCHAR(64)"
            elif 'hash' in name.lower():
                sql_type = "VARCHAR(64)"  # SHA256 hex = 64 chars
            elif 'url' in name.lower() or 'img' in name.lower():
                sql_type = "VARCHAR(512)"
            elif 'content' in name.lower() or 'json' in name.lower() or 'text' in name.lower() or 'remark' in name.lower() or 'signature' in name.lower():
                sql_type = "TEXT"
            elif 'raw_json' in name.lower():
                sql_type = "TEXT"
            else:
                sql_type = "VARCHAR(255)"

        # 默认值
        default = ""
        fill = fill_map.get(name)
        if fill in ('INSERT', 'INSERT_UPDATE'):
            default = " NOT NULL DEFAULT CURRENT_TIMESTAMP"
            if fill == 'INSERT_UPDATE':
                default = " NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
        elif name in ('created_at', 'updated_at'):
            default = " NOT NULL DEFAULT CURRENT_TIMESTAMP"
            if name == 'updated_at':
                default = " NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"

        # 主键
        if name == 'id':
            fields.append((snake_name, "BIGINT", " NOT NULL AUTO_INCREMENT PRIMARY KEY"))
        elif name == 'deleted':
            fields.append((snake_name, "TINYINT(1)", " NOT NULL DEFAULT 0"))
        elif name in ('created_at', 'updated_at'):
            if fill == 'INSERT':
                fields.append((snake_name, sql_type, " NOT NULL DEFAULT CURRENT_TIMESTAMP"))
            elif fill == 'INSERT_UPDATE':
                fields.append((snake_name, sql_type, " NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"))
            else:
                fields.append((snake_name, sql_type, " NOT NULL DEFAULT CURRENT_TIMESTAMP"))
        else:
            # 默认 NULL 允许
            fields.append((snake_name, sql_type, " NULL"))

    # 简单索引
    indexes = []
    if 'customer_id' in [f[0] for f in fields]:
        indexes.append("INDEX idx_customer (customer_id)")
    if 'session_id' in [f[0] for f in fields]:
        indexes.append("INDEX idx_session (session_id)")
    if 'agent_username' in [f[0] for f in fields]:
        indexes.append("INDEX idx_agent (agent_username)")
    if 'status' in [f[0] for f in fields]:
        indexes.append("INDEX idx_status (status)")
    if 'created_at' in [f[0] for f in fields]:
        indexes.append("INDEX idx_created_at (created_at)")

    return (table, cls, fields, indexes)


def collect_all():
    """收集所有 domain"""
    result = []
    for root in ['cs-auth/src/main/java/com/example/auth/domain',
                 'cs-im/src/main/java/com/example/im/domain']:
        full = ROOT / root
        if not full.exists(): continue
        for f in sorted(full.glob('*.java')):
            d = parse_domain(f)
            if d:
                result.append(d)
    return result


def generate_sql():
    domains = collect_all()
    cs_auth = [d for d in domains if 'auth' in str(d[0]) or d[0] in ('wechat_user', 'user_token', 'audit_log', 'blacklist')]
    cs_im = [d for d in domains if d not in cs_auth]

    # 简化：手动分组
    auth_tables = {'wechat_user', 'user_token', 'audit_log', 'blacklist'}
    cs_auth = [d for d in domains if d[0] in auth_tables]
    cs_im = [d for d in domains if d[0] not in auth_tables]

    out = []
    out.append("-- =====================================================")
    out.append("-- OnlineChat 一键初始化脚本（v2.2.3 自动生成）")
    out.append(f"-- 数据库：MySQL 8.0.46+ / MariaDB 10.5+")
    out.append(f"-- 生成时间：自动")
    out.append(f"-- 字符集：utf8mb4 / 排序：utf8mb4_unicode_ci / 引擎：InnoDB")
    out.append(f"-- 说明：从所有 @TableName domain 类自动生成")
    out.append("-- =====================================================")
    out.append("-- 用法：")
    out.append("--   mysql -uroot -p < db_init_all.sql")
    out.append("-- =====================================================")
    out.append("")
    out.append("-- ============================================================")
    out.append("-- 0. 创建数据库")
    out.append("-- ============================================================")
    out.append("CREATE DATABASE IF NOT EXISTS cs_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    out.append("CREATE DATABASE IF NOT EXISTS cs_im CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    out.append("")

    # cs-auth
    out.append("-- ============================================================")
    out.append(f"-- 1. cs-auth（{len(cs_auth)} 张表）")
    out.append("-- ============================================================")
    out.append("USE cs_auth;")
    out.append("")
    for table, cls, fields, indexes in cs_auth:
        out.append(f"-- {cls}（自动生成）")
        out.append(f"DROP TABLE IF EXISTS {table};")
        out.append(f"CREATE TABLE {table} (")
        for i, (col, sql_type, suffix) in enumerate(fields):
            sep = "," if i < len(fields) - 1 or indexes else ""
            out.append(f"    {col:<22}{sql_type}{suffix}{sep}")
        for idx in indexes:
            out.append(f"    {idx},")
        # 去尾部逗号
        if out[-1].endswith(','):
            out[-1] = out[-1][:-1]
        out.append(f") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{cls}';")
        out.append("")

    # cs-im
    out.append("-- ============================================================")
    out.append(f"-- 2. cs-im（{len(cs_im)} 张表）")
    out.append("-- ============================================================")
    out.append("USE cs_im;")
    out.append("")
    for table, cls, fields, indexes in cs_im:
        out.append(f"-- {cls}（自动生成）")
        out.append(f"DROP TABLE IF EXISTS {table};")
        out.append(f"CREATE TABLE {table} (")
        for i, (col, sql_type, suffix) in enumerate(fields):
            sep = "," if i < len(fields) - 1 or indexes else ""
            out.append(f"    {col:<22}{sql_type}{suffix}{sep}")
        for idx in indexes:
            out.append(f"    {idx},")
        if out[-1].endswith(','):
            out[-1] = out[-1][:-1]
        out.append(f") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{cls}';")
        out.append("")

    # 预置数据
    out.append("-- ============================================================")
    out.append("-- 3. 演示数据")
    out.append("-- ============================================================")
    out.append("USE cs_im;")
    out.append("")
    out.append("INSERT INTO product (product_code, name, product_type, risk_level, min_amount, yield_rate, max_amount, period, description) VALUES")
    out.append("('FUND-001', '稳赢 30 天', 'FUND', 'LOW', 100.0000, 3.2000, 1000000.0000, 30, '30 天短期理财');")
    out.append("")

    out.append("-- ============================================================")
    out.append("-- 4. 验证")
    out.append("-- ============================================================")
    out.append("SELECT 'cs_auth' AS db, COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'cs_auth'")
    out.append("UNION ALL")
    out.append("SELECT 'cs_im', COUNT(*) FROM information_schema.tables WHERE table_schema = 'cs_im';")

    return "\n".join(out)


if __name__ == "__main__":
    sql = generate_sql()
    with open(SQL_FILE, "w") as f:
        f.write(sql)
    print(f"✅ 生成 {SQL_FILE}")
    print(f"   大小：{len(sql)} 字节 / {sql.count(chr(10))} 行")