# 数据库（MySQL 8.x）

OnlineChat v1.7.1 数据库从 H2 内存数据库切换到 **MySQL 8.x + Druid 连接池 + Flyway 迁移**。

## 架构

按业务域拆分 **3 个数据库**（避免单库过大 + 故障域隔离）：

| 数据库 | 服务 | 主要表 |
|---|---|---|
| `cs_auth` | cs-auth | wechat_user / user_token / audit_log / blacklist |
| `cs_im` | cs-im | chat_session / chat_message / file_upload / agent_queue / sensitive_word |
| `cs_trade` | cs-trade | customer_account / trade_record / product / orders |

## 连接配置

通过环境变量注入（**生产禁止硬编码**）：

```bash
export MYSQL_HOST=mysql.internal
export MYSQL_PORT=3306
export MYSQL_USER=cs_app
export MYSQL_PASSWORD=<strong-password>
export MYSQL_DB=cs_auth
```

## 一件启动（Docker）

```bash
# 1. 启动 MySQL + Redis + Nacos + 5 个微服务
docker-compose up -d

# 2. 查看状态
docker-compose ps

# 3. 查看日志
docker-compose logs -f cs-auth

# 4. 验证健康
curl http://localhost:9001/actuator/health
curl http://127.0.0.1:9000/druid/  # Druid 监控（admin/admin123）
```

## Flyway 迁移

启动时自动执行 `db/migration/V{version}__{description}.sql`：

```
cs-auth/src/main/resources/db/migration/
└── V1.0.0__init_auth_schema.sql

cs-im/src/main/resources/db/migration/
└── V1.0.0__init_im_schema.sql

cs-trade/src/main/resources/db/migration/
└── V1.0.0__init_trade_schema.sql
```

新增迁移规范：
- `V1.0.1__add_user_avatar.sql`
- `V1.1.0__add_product_category.sql`
- **禁止修改已发布版本**：永远新增新版本号

## Druid 连接池

每个服务默认配置：

| 参数 | cs-auth/cs-trade | cs-im |
|---|---|---|
| initial-size | 5 | 5 |
| min-idle | 5 | 5 |
| max-active | 20 | 30 |
| max-wait | 60000ms | 60000ms |
| validation-query | SELECT 1 | SELECT 1 |
| filters | stat,wall,slf4j | stat,wall,slf4j |

监控面板：`http://localhost:{port}/druid/`（dev 环境，admin/admin123）

生产环境**关闭** Druid Web UI（避免泄露 SQL 监控）。

## 性能调优（my.cnf）

```ini
max_connections=500              # 总连接数上限
innodb_buffer_pool_size=512M     # 缓冲池（建议物理内存 50%）
innodb_log_file_size=128M
innodb_flush_log_at_trx_commit=2 # 高性能模式（牺牲 1s 数据安全）
sync_binlog=0
slow_query_log=1
long_query_time=2                # 记录 > 2s 的慢查询
```

## 数据备份

```bash
# 全量备份
docker exec cs-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction --routines --triggers > /tmp/backup.sql'
docker cp cs-mysql:/tmp/backup.sql ./backup-$(date +%Y%m%d).sql

# 还原
docker exec -i cs-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < backup-20260626.sql
```

## 主从复制（生产可选）

```ini
# 主库 my.cnf
server-id=1
log-bin=mysql-bin
binlog-format=ROW
gtid-mode=on
enforce-gtid-consistency=on

# 从库 my.cnf
server-id=2
relay-log=relay-bin
read-only=1
gtid-mode=on
enforce-gtid-consistency=on
```

服务通过 `MYSQL_HOST` 指向主库，从库走读流量代理（MyCAT / ShardingSphere）。

## Profile 切换

```bash
# 开发（默认） - 同主机 MySQL
java -jar app.jar

# 测试 - H2 内存
java -jar app.jar --spring.profiles.active=test

# 生产 - 强 SSL + 关闭 Druid UI
java -jar app.jar --spring.profiles.active=prod
```

## 数据迁移（H2 → MySQL）

```bash
# 1. H2 导出
mvn test -Dtest=H2DumpTest  # 见 AuthService/H2DumpTest.java

# 2. MySQL 导入
mysql -h $MYSQL_HOST -uroot -p cs_auth < dump-auth.sql
mysql -h $MYSQL_HOST -uroot -p cs_im < dump-im.sql
mysql -h $MYSQL_HOST -uroot -p cs_trade < dump-trade.sql
```

## 监控指标

Druid 暴露的指标（通过 Prometheus / Spring Actuator）：

| 指标 | 含义 |
|---|---|
| `druid_active_count` | 活跃连接数 |
| `druid_pooling_count` | 池中空闲连接数 |
| `druid_connect_count` | 总连接次数 |
| `druid_close_count` | 关闭连接次数 |
| `druid_error_count` | 错误次数 |
| `druid_execute_count` | SQL 执行次数 |
| `druid_execute_query_count` | 查询次数 |
| `druid_execute_update_count` | 更新次数 |
| `druid_execute_batch_count` | 批量执行次数 |

## 安全

1. **应用账号**：使用 `cs_app`（非 root），只授权 3 个业务库
2. **SSL/TLS**：生产环境 `useSSL=true` + `requireSSL=true`
3. **连接加密**：Druid `encrypt=true` + `config.decrypt=true`（密码加密存储在配置中心）
4. **SQL 防火墙**：`druid.filters=wall` 拦截 SQL 注入
5. **审计**：开启 MySQL `general_log` 记录所有连接

## 常见问题

### 1. MySQL 8 认证插件报错
```
Client does not support authentication protocol requested by server
```
解决：执行 `ALTER USER 'cs_app'@'%' IDENTIFIED WITH mysql_native_password BY 'cs_app_password_2024';`

### 2. 时区问题
确保 MySQL 时区 + JVM 时区一致：
```sql
SET GLOBAL time_zone = '+08:00';
```
或 `my.cnf` 加 `default-time-zone='+08:00'`。

### 3. Flyway baseline 失败
启动报错说明数据库非空。设置 `baseline-on-migrate: true` 或手动执行：
```sql
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (1, '1', '<< Baseline >>', 'BASELINE', '<< Baseline >>', NULL, 'cs_app', NOW(), 0, true);
```