# 功能验证图（E2E 时序图）

本图覆盖所有核心业务流。可作为回归测试 checklist。

---

## 图例

```
[客户H5]   [公众号]   [企业微信]   [API网关]   [cs-auth]   [cs-robot]   [cs-im]   [cs-trade]   [Redis]   [坐席H5]
```

---

## 流程 1：客户静默登录（首次进入）

```
客户H5               网关                 cs-auth             Redis
  │                   │                     │                  │
  │──GET /customer/──>│                     │                  │
  │                   │──GET /auth/silent──>│                  │
  │                   │                     │                  │
  │                   │                     │ (查 openid) ────>│
  │                   │                     │<───miss──────────│
  │                   │                     │ 创建 WechatUser  │
  │                   │                     │ issue JWT        │
  │                   │<───token────────────│                  │
  │<───html + JWT─────│                     │                  │
  │                   │                     │                  │
  ▼                   ▼                     ▼                  ▼
   进入聊天页（ROBOT 状态）                      
```

**验证点**：
- ✅ 客户无需输入密码即拿到 JWT
- ✅ token 写入 `localStorage`，刷新页面不丢
- ✅ 服务端写 `token:{customerId}` 到 Redis（24h TTL）

---

## 流程 2：客户与机器人对话（默认 + 富文本）

```
客户H5           cs-im                cs-robot        TradeClient        cs-trade
  │                │                     │                │                  │
  │ POST /im/customer/chat                │                │                  │
  │ body: {text: "查账单"}                │                │                  │
  │───────────────>│                     │                │                  │
  │                │ 存档客户消息         │                │                  │
  │                │ 检查 session.status  │                │                  │
  │                │   == ROBOT          │                │                  │
  │                │──chat(text)─────────>│                │                  │
  │                │                     │ 关键词匹配      │                  │
  │                │                     │ 返回 RichMessage│                  │
  │                │<──RichMessage───────│                │                  │
  │                │                     │                │                  │
  │ (查账单时)      │                     │                │                  │
  │                │─────────────────────┼────────────────>│                  │
  │                │                     │                │ GET /trade/bills  │
  │                │                     │                │─────────────────>│
  │                │                     │                │<──List<Bill>─────│
  │                │                     │                │                  │
  │                │ 保存 Rich 消息       │                │                  │
  │                │ 推送 /topic/customer │                │                  │
  │<── ws push ───│                     │                │                  │
  │                │                     │                │                  │
  ▼                ▼                     ▼                ▼                  ▼
```

**验证点**：
- ✅ 客户消息存档到 `chat_message`（type=TEXT）
- ✅ 机器人响应存档为 type=RICH + payloadJson
- ✅ 富文本推送到 WS，客户端渲染为卡片
- ✅ "查账单"触发 cs-trade 调用，返回 Bill 列表，包装成 BILL 类型富文本

---

## 流程 3：转人工（关键词触发）

```
客户H5              cs-im              cs-robot             坐席H5
  │                  │                    │                    │
  │ 发送"人工"        │                    │                    │
  │─────────────────>│                    │                    │
  │                  │──chat()──────────>│                    │
  │                  │<──RichMessage──────│                    │
  │                  │  (转人工卡片)       │                    │
  │                  │                    │                    │
  │                  │ transferToQueue()  │                    │
  │                  │ session.status     │                    │
  │                  │  ROBOT → QUEUED    │                    │
  │                  │                    │                    │
  │                  │── broadcast ───────────────────────────────────────>│
  │                  │   /topic/agents    │                    │  (排队列表刷新)
  │                  │   QUEUE_JOINED     │                    │
  │                  │                    │                    │
  │                  │<─── accept ───────────────────────────│
  │                  │                    │                    │
  │                  │ acceptByAgent()    │                    │
  │                  │ status: QUEUED     │                    │
  │                  │        → IN_SESSION│                    │
  │                  │                    │                    │
  │<── ws AGENT_JOINED ─────────────────────────────────────────│
  │                  │──/topic/agent/X ACCEPTED ──────────────>│
  │                  │                    │                    │
  ▼                  ▼                    ▼                    ▼
```

**验证点**：
- ✅ 状态机：ROBOT → QUEUED → IN_SESSION
- ✅ 排队信息广播到 `/topic/agents`，所有坐席可见
- ✅ 坐席接听后：客户收到 AGENT_JOINED、坐席收到 ACCEPTED、队列移除
- ✅ 审计日志写入 `audit_log`（TRANSFER_QUEUE + ACCEPT 两条）

---

## 流程 4：办理交易（前置鉴权 + 联网核查）

```
客户H5         cs-im           网关           cs-trade         RiskCheck Mock
  │              │              │                │                  │
  │ "💼办理业务" │              │                │                  │
  │              │ 弹出交易窗    │                │                  │
  │ 填金额+身份证 │              │                │                  │
  │ POST /trade/orders         │                │                  │
  │   (带 Authorization)       │                │                  │
  │─────────────>│              │                │                  │
  │              │ JwtGlobalFilter              │                  │
  │              │ 解析 X-User-Role/Id          │                  │
  │              │ 注入 Header                   │                  │
  │              │──────────────>│              │                  │
  │              │              │──POST /trade/orders            │
  │              │              │                │                  │
  │              │              │                │ 检查未登录 (401)  │
  │              │              │                │ 检查 FAIL 前缀    │
  │              │              │                │ 调用 mock:        │
  │              │              │                │──POST /mock──────>│
  │              │              │                │<──{result:FAIL}──│
  │              │              │                │ 拒单 (403)        │
  │              │              │<────403────────│                  │
  │<──── 失败 ──│              │                │                  │
  │              │              │                │                  │
  │ 改正后再提交 (不带 FAIL)    │                │                  │
  │─────────────>│              │                │                  │
  │              │              │──POST─────────>│                  │
  │              │              │                │──mock───────────>│
  │              │              │                │<──{result:PASS}──│
  │              │              │                │ 写 Bill (SUCCESS)│
  │              │              │<────200────────│                  │
  │<────成功 ────│              │                │                  │
  ▼              ▼              ▼                ▼                  ▼
```

**验证点**：
- ✅ 未登录 → 401（网关层拦截）
- ✅ 身份证 `FAIL` 前缀 → 联网核查 mock 返回 FAIL → 服务端 403
- ✅ 通过 → 写 `bill` 表，status=SUCCESS
- ✅ 审计：业务敏感操作留痕

---

## 流程 5：坐席挂断 / 超时 / 强制挂断

```
坐席挂断:
  坐席 ──POST /im/agent/hangup──> cs-im
  cs-im: status → ENDED, endedBy=AGENT
  cs-im: broadcast /topic/customer/{cid} ENDED
  cs-im: 审计 HANGUP

超时自动挂断（5 分钟无活动）:
  SessionTimeoutScheduler @Scheduled(fixedDelay=30s)
  scan() 遍历 IN_SESSION + lastActiveAt > 5min
  sessionService.hangupBySystem()
  status → ENDED, endedBy=SYSTEM

管理员强制挂断:
  Admin ──POST /im/admin/sessions/{id}/force-hangup?reason=...
  SecurityContextHolder.requireRole("ADMIN") → 403 if not admin
  sessionService.forceHangup()
  status → ENDED, endedBy=ADMIN
  broadcast ENDED + 写审计 ADMIN_FORCE_HANGUP
```

**验证点**：
- ✅ 三种挂断方：CUSTOMER / AGENT / SYSTEM / ADMIN
- ✅ 审计日志可查询 `action=HANGUP` 或 `ADMIN_FORCE_HANGUP`

---

## 流程 6：离线消息推送（客户关闭页面）

```
客户H5 关闭 / 杀进程
   │
   ▼ WS disconnect 事件
cs-im: WsConnectListener.onDisconnect()
   markOffline(cid) → redis delete online:cid
   │
   ▼ 机器人后续消息 / 坐席消息入站
cs-im: 检查 isOnline(cid) = false
   OfflinePushService.enqueue(cid, "OA", payload)
   写入 redis stream "offline-msg"
   │
   ▼ 30 秒定时扫描
OfflinePushService.flush()
   读取 stream → 用户仍离线 → 调用 WechatOaClient.sendTemplateMsg()
   │
   ▼
   公众号推送模板消息到客户微信
   （Mock 模式仅写日志）
   │
   ▼ 客户重新进入 H5
cs-im: WsConnectListener.onConnected()
   markOnline(cid) → redis set online:cid (TTL 2min)
```

**验证点**：
- ✅ Redis 在线标记 2 分钟 TTL（防止心跳断开残留）
- ✅ Stream 消息保留直到推送成功
- ✅ 重新连上后停止推送（`isOnline` 返回 true）

---

## 流程 7：历史回溯

```
客户点击 📂 图标
  GET /im/customer/sessions
  → cs-im: listByCustomer(customerId)
  → 返回 List<ChatSession>（按 id 倒序）

客户点击某个会话
  GET /im/customer/messages?sessionId=N
  → cs-im: messagesOf(N)
  → 返回 List<ChatMessage>（按 id 升序）

渲染：富文本消息直接展示 payload（type=PRODUCT/BILL/CARD）
```

---

## 流程 8：合规审计

```
所有以下操作自动写入 audit_log：
  • LOGIN - 静默登录 / OAuth / 管理员登录
  • TRANSFER_QUEUE - 客户转人工
  • ACCEPT - 坐席接听
  • HANGUP - 挂断
  • ADMIN_FORCE_HANGUP - 管理员强制挂断
  • TRADE_ORDER - 交易下单（敏感）

审计字段：
  - action / targetType / targetId
  - userId / userRole / ip / traceId
  - detail (JSON, 含脱敏后的请求参数)
  - createdAt

管理员查询：GET /im/admin/audit-logs?action=xxx&page=0&size=20
```

---

## 接口覆盖清单（合规：每个端点都有测试）

### cs-auth
| 方法 | 路径 | 测试 |
|---|---|---|
| POST | /auth/silent-login | ✅ AuthEndToEndTest |
| GET  | /auth/wechat-oa/authorize | ✅ |
| GET  | /auth/wechat-oa/callback | ✅ AuthEndToEndTest |
| GET  | /auth/wechat-work/authorize | ✅ |
| GET  | /auth/wechat-work/callback | ✅ |
| POST | /auth/admin/login | ✅ AuthEndToEndTest |
| POST | /auth/refresh | ✅ |

### cs-robot
| 方法 | 路径 | 测试 |
|---|---|---|
| POST | /robot/chat | ✅ ImEndToEndTest |
| GET  | /robot/greeting | ✅ |

### cs-im
| 方法 | 路径 | 测试 |
|---|---|---|
| GET  | /im/customer/session/active | ✅ |
| POST | /im/customer/session/transfer-to-agent | ✅ |
| POST | /im/customer/session/hangup | ✅ |
| POST | /im/customer/chat | ✅ |
| GET  | /im/customer/sessions | ✅ |
| GET  | /im/customer/messages | ✅ |
| GET  | /im/customer/bills | ✅ |
| GET  | /im/customer/products | ✅ |
| GET  | /im/agent/queue | ✅ |
| POST | /im/agent/accept | ✅ |
| POST | /im/agent/hangup | ✅ |
| GET  | /im/agent/sessions | ✅ |
| GET  | /im/admin/sessions | ✅ |
| POST | /im/admin/sessions/{id}/force-hangup | ✅ ImEndToEndTest |
| GET  | /im/admin/audit-logs | ✅ |
| GET  | /im/admin/stats | ✅ ImEndToEndTest |
| WS   | /ws (SockJS) | ✅ |
| WS   | /topic/customer/{cid} | ✅ |
| WS   | /topic/agent/{username} | ✅ |
| WS   | /topic/agents | ✅ |
| WS   | /app/customer/chat | ✅ |
| WS   | /app/agent/chat | ✅ |

### cs-trade
| 方法 | 路径 | 测试 |
|---|---|---|
| GET  | /trade/bills/recent | ✅ TradeEndToEndTest |
| GET  | /trade/products | ✅ TradeEndToEndTest |
| POST | /trade/orders | ✅ |

### cs-gateway
| 方法 | 路径 | 测试 |
|---|---|---|
| ALL  | /auth/** | ✅ Gateway |
| ALL  | /robot/** | ✅ Gateway |
| ALL  | /im/**, /ws/** | ✅ Gateway |
| ALL  | /trade/** | ✅ Gateway |
| -    | JwtGlobalFilter | ✅ (路由级集成) |

---

## 合规化 checklist

| 等保要求 | 实现 |
|---|---|
| 身份鉴别（双因素） | 公众号 OAuth（基于微信）+ 静默登录；坐席企微 OAuth |
| 访问控制（最小权限） | JWT + Role 区分 CUSTOMER / AGENT / ADMIN；`requireRole()` 强制 |
| 审计日志 | `AuditService` 异步写 `audit_log` 表，含 traceId/IP/详情 |
| 数据脱敏 | `SensitiveUtils.maskMobile/IdCard/Name` |
| 通信保密 | 建议生产用 HTTPS（nginx 终止）|
| 入侵防范 | 联网核查前置 + 敏感词过滤（v1.5.3 引入，本期保留）|
| 恶意代码防范 | 客户交易 payload 含 `bizPayload` 长度限制 |
| 敏感操作二次确认 | 管理员强制挂断必填原因（前端校验 + 后端校验）|