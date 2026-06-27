# Kafka 异步消息 + 离线消息处理

OnlineChat v1.8.0 引入 **Apache Kafka** 作为异步消息中间件，解决：

1. **解耦**：IM 服务只负责接收 → 入库 → 发 Kafka，不再直连 WebSocket
2. **削峰**：高峰期消息写入 Kafka 异步消费，避免下游服务被打垮
3. **离线消息**：用户不在线时暂存 Redis，上线后拉取
4. **多实例**：cs-message / cs-im 多个实例通过 Kafka 消费者组负载均衡
5. **可追溯**：所有消息事件持久化到 Kafka（默认 7 天），可重放 / 审计

## 架构

```
┌─────────┐    1.REST    ┌─────────┐    2.Kafka    ┌──────────────┐
│ Customer│──────────────▶│  cs-im  │──────────────▶│ Kafka Topics │
└─────────┘              │  (生产) │              │ ┌──────────┐ │
                         └─────────┘              │ │CUSTOMER  │ │
                              │                   │ │AGENT     │ │
                              │ 3.入库            │ │SYSTEM    │ │
                              ▼                   │ │PRESENCE  │ │
                         ┌─────────┐              │ └──────────┘ │
                         │   MySQL │              └──────┬───────┘
                         └─────────┘                     │
                                                        │ 4.消费
                                                ┌───────▼────────┐
                                                │   cs-message   │
                                                │   (消费者)     │
                                                └───────┬────────┘
                                                        │
                              ┌─────────────────────────┼─────────────────────────┐
                              │ 5.在线检查              │ 5.在线检查              │
                              ▼                         ▼                         ▼
                        ┌──────────┐             ┌──────────┐             ┌──────────┐
                        │  推 Redis │             │  存 Redis │             │  持久化  │
                        │  Pub/Sub │             │  离线队列 │             │  审计    │
                        └────┬─────┘             └────┬─────┘             └──────────┘
                             │                       │
                             ▼                       ▼
                        ┌──────────┐          ┌──────────┐
                        │ cs-im    │          │ 上线拉取 │
                        │ WebSocket│          │ (REST)   │
                        └────┬─────┘          └──────────┘
                             │
                             ▼
                        ┌──────────┐
                        │ Customer │
                        │ Agent    │
                        └──────────┘
```

## Kafka 主题

| 主题 | 分区 | 副本 | 用途 |
|---|---|---|---|
| `chat.customer.message` | 3 | 1 | 客户发送的消息 |
| `chat.agent.message` | 3 | 1 | 坐席发送的消息 |
| `chat.system.message` | 3 | 1 | 系统消息（强制挂断 / 结束 / 转接）|
| `chat.message.read` | 3 | 1 | 消息已读回执 |
| `chat.session.event` | 3 | 1 | 会话事件 |
| `chat.presence.event` | 3 | 1 | 用户上线 / 下线 |
| `chat.audit.event` | 3 | 1 | 关键操作审计 |
| `chat.dlq` | 3 | 1 | 死信队列（失败消息）|

**分区数 = 3**：保证顺序（同 sessionId）+ 3 实例负载均衡

**键策略**：用 `sessionId` 作 key → 同会话消息有序
```java
kafkaTemplate.send("chat.agent.message", sessionId, payload);
```

## 消费者组

| 服务 | 消费者组 | 实例数 |
|---|---|---|
| cs-message | `cs-message-group` | 1+（多实例负载）|
| cs-im | `cs-im-group` | 1+ |

## 离线消息存储

数据结构（Redis）：
```
Key: offline:msg:{userId}
Type: List<JSON>
Max Length: 100（裁剪）
TTL: 7 天
```

**写入（消息到达时）**：
```java
LPUSH offline:msg:user-001 '{"msgId":"m1","content":"hello"}'
LTRIM offline:msg:user-001 0 99
EXPIRE offline:msg:user-001 604800
```

**拉取（用户上线时）**：
```java
LRANGE offline:msg:user-001 0 -1
DEL offline:msg:user-001
```

## 在线上线（Presence）

WebSocket 连接建立 → `KafkaTopics.PRESENCE_EVENT (ONLINE)` → cs-message 消费 → `SET presence:user:{userId} connId`
WebSocket 断开 → `KafkaTopics.PRESENCE_EVENT (OFFLINE)` → cs-message 消费 → `DEL`

**TTL 60s**：客户端需 30s 心跳一次，否则视为离线。

## 死信队列（DLQ）

消费失败后：
1. 指数退避重试：1s → 2s → 4s（最多 3 次，30s 内）
2. 最终失败 → 发送到 `chat.dlq`
3. DLQ 单独监控 + 告警

## Kafka 性能调优

**生产者**（cs-im）：
```
acks=all                  # 强一致
enable.idempotence=true   # 幂等
compression.type=lz4      # 压缩
linger.ms=20              # 批量发送
batch.size=32768          # 32KB
```

**消费者**（cs-message / cs-im）：
```
enable.auto.commit=false  # 手动 ack
max.poll.records=50       # 单次拉取
session.timeout.ms=30000  # 30s 心跳
auto.offset.reset=earliest
```

**Broker**（KRaft 模式）：
```
num.partitions=3
default.replication.factor=1  # 单节点，生产应 3
min.insync.replicas=1
log.retention.hours=168        # 7 天
log.segment.bytes=1073741824   # 1GB
```

## Kafka UI

启动后访问 `http://localhost:8081/`：
- 查看主题 / 分区 / offset
- 消息内容浏览
- 消费者组 lag 监控

## 测试

启动完整栈：
```bash
docker-compose up -d
docker-compose ps
docker-compose logs -f cs-message
```

测试发送消息：
```bash
# 1. 登录获取 token
TOKEN=$(curl -s -X POST http://127.0.0.1:9000/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"test123"}' | jq -r .data.token)

# 2. 发送消息
curl -X POST http://127.0.0.1:9000/im/send \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"s1","content":"hello kafka"}'

# 3. 查看 Kafka UI
open http://localhost:8081/

# 4. 用户下线 → 发 5 条消息 → 上线 → 拉取
curl http://localhost:9005/message/offline/user-001
```

## FAQ

### Q1: 消息顺序问题
A: 用 sessionId 作分区 key，同一会话消息有序。**不要**用 userId，否则多端登录时乱序。

### Q2: 重复消费
A: Kafka 至少一次语义 + 消费者幂等：
- 数据库：消息唯一索引（msg_id UNIQUE）
- WebSocket 推送：客户端按 msgId 去重

### Q3: 离线消息丢失
A: 多重保障：
1. Redis 持久化（AOF + RDB）
2. 离线消息 TTL 7 天（足够用户上线）
3. Kafka 兜底（重放历史消息）

### Q4: 性能瓶颈
A: 监控指标 → 按需扩容：
- 消费者 lag > 1000 → 扩 cs-message 实例
- Kafka 磁盘 > 80% → 扩分区 + 缩短保留期
- Redis 内存 > 70% → 缩短离线 TTL + 减小 maxMessages