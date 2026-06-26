# v1.9.0 新功能文档

## 1. 工单系统（Ticket）

### 业务模型

`cs_im.ticket` 表 + `cs_im.ticket_reply` 表，支持工单全生命周期。

### 状态机

```
   OPEN ──► ASSIGNED ──► PROCESSING ──► RESOLVED ──► CLOSED
    │           │             │              ▲
    └──► CANCELLED           └──────────────┘  (客户对 RESOLVED 不满意)
```

### 优先级 & SLA

| 优先级 | SLA 截止 |
|--------|----------|
| URGENT | 1 小时 |
| HIGH   | 4 小时 |
| NORMAL | 24 小时 |
| LOW    | 72 小时 |

### REST API

| 方法 | 路径 | 角色 | 说明 |
|------|------|------|------|
| POST | /im/ticket/create | CUSTOMER/AGENT | 创建工单 |
| GET  | /im/ticket/{ticketNo} | 任意 | 工单详情 + 回复 |
| GET  | /im/ticket/list?customerId | CUSTOMER | 我的工单 |
| GET  | /im/ticket/queue | AGENT | 排队工单（按优先级） |
| GET  | /im/ticket/mine | AGENT | 我的工单 |
| POST | /im/ticket/{ticketNo}/assign | AGENT | 认领 |
| POST | /im/ticket/{ticketNo}/start | AGENT | 开始处理 |
| POST | /im/ticket/{ticketNo}/resolve | AGENT | 标记解决 |
| POST | /im/ticket/{ticketNo}/close | AGENT | 关闭 |
| POST | /im/ticket/{ticketNo}/cancel | 任意 | 取消 |
| POST | /im/ticket/{ticketNo}/reply | 任意 | 回复 |

## 2. 知识库 FAQ

### 数据模型

- `cs_im.faq_category` — 分类（支持父子两级）
- `cs_im.faq` — 问答（question / answer / keywords）

### 检索策略

`cs_im.faq.searchByKeyword` 用 LIKE 模糊匹配 `question / keywords / answer`，按 `view_count` 降序，限 20 条。

### 机器人集成

`RobotEngine.handle()` 在关键词未命中时调 `FaqService.search(text)`，取 Top 1 作为回复。

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /im/faq/search?keyword= | 搜索 |
| GET  | /im/faq/top | 热门 |
| GET  | /im/faq/category/top | 顶级分类 |
| GET  | /im/faq/category/{id} | 子分类 |
| POST | /im/faq/{id}/view | 浏览 +1 |
| POST | /im/faq/create | 创建（管理员） |

## 3. 视频回溯（Video Replay）

### 实现方式

**当前版本：基于消息时间戳的"伪视频"回放**
- 每条消息按 createdAt 排序
- 间隔 = (最后消息 - 第一消息) / 消息数
- 前端用 setInterval 推进 currentTimeMs，按 offsetMs 切换当前帧

**生产升级路径**：
- 后端用 Playwright / ffmpeg 截图预渲染视频帧
- 关联 ffmpeg 合成 MP4 视频，存入 OSS / S3
- 前端用 video.js 播放

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /im/replay/{sessionId} | 拉取回放帧数据 |
| POST | /im/replay/{sessionId}/video-url | 设置完整视频 URL |
| POST | /im/replay/message/{messageId}/frame-url | 设置单帧截图 URL |

### 返回结构

```json
{
  "sessionId": 123,
  "customerId": "real_xxx",
  "agentUsername": "agent01",
  "startTime": "2026-06-26T10:00:00",
  "endTime": "2026-06-26T10:15:30",
  "frameCount": 47,
  "frames": [
    {
      "index": 0,
      "messageId": 1,
      "fromUser": "real_xxx",
      "fromRole": "CUSTOMER",
      "content": "你好",
      "type": "TEXT",
      "timestamp": "...",
      "offsetMs": 0
    }
  ],
  "videoUrl": null
}
```

## 4. 实时聊天（WebSocket 保留 + 离线消息兜底）

### 真实业务场景

```
客户 ──WS──> cs-im ──Kafka──> 接收方
                              ├─ 在线 → WebSocket 推送
                              └─ 离线 → Redis List (OfflineMessageStore)
                                    → 重连后 drain
```

### WebSocket 主题

| 客户端订阅 | 服务端推送 |
|------------|------------|
| `/user/queue/messages` | 实时消息 / AGENT_JOINED / AGENT_LEFT / ENDED |
| `/user/queue/offline` | 离线消息批量推送（重连后） |

### 离线消息存储

- Redis List：`offline:msg:session:{sessionId}`
- LPUSH + LTRIM（最多 100 条）
- TTL 7 天
- `OfflineMessageStore.drain(sessionId)` 弹出全部

### 无坐席提示

Customer.vue 顶部条幅：
- **无坐席**（智能客服模式）：🤖 + "当前无坐席在线（智能客服模式）" + "转人工" 按钮
- **排队中**：⏳ + "正在为你排队等待坐席..." + disabled 按钮
- **服务中**：🎧 + "坐席 agent01 正在为你服务"
- **已结束**：🛑 + "会话已结束"

## 5. JWT 密钥修复

`application.yml` 的 `cs.jwt.secret` 从占位符改为生产密钥：

```yaml
cs:
  jwt:
    secret: ZLmASTkUGsHRGeHW8i3-Fk1Hw39lEHIK
```

应用：`cs-auth` + `cs-gateway` 的 application.yml。

## 6. PWA（渐进式 Web 应用）

### 文件

- `/manifest.webmanifest` — 应用清单
- `/sw.js` — Service Worker（Cache First 静态 + Network First API）

### 入口

`index.html` 加：
- `<link rel="manifest" href="/manifest.webmanifest" />`
- `<meta name="apple-mobile-web-app-capable" content="yes" />`
- CSP 加 `worker-src 'self'`
- JS 注册 SW：`navigator.serviceWorker.register('/sw.js')`

### 离线能力

API 请求失败 → 返回缓存 → 用户体验"暂不可用但有缓存"

## 7. 代码注释（每行）

v1.9.0 所有新增/修改的 Java 文件 + Vue 组件均含详细中文注释：
- 类级 Javadoc
- 字段注释
- 方法签名注释
- 关键逻辑 inline 注释

## 8. 集成测试

`cs-im/src/test/java/com/example/im/TicketIntegrationTest.java` 4 个测试：
1. testTicketFullFlow — 工单完整 6 步状态机
2. testTicketCancel — 取消工单
3. testFaqSearch — FAQ 搜索
4. testFaqTop — 热门 FAQ

全 **4/4 PASS**。

## 9. 提交统计

| 模块 | 变更 |
|------|------|
| cs-im 实体 | +6 (Ticket, TicketReply, Faq, FaqCategory, +ChatSession.videoReplayUrl, +ChatMessage.videoFrameUrl) |
| cs-im Mapper | +4 (TicketMapper, TicketReplyMapper, FaqMapper, FaqCategoryMapper) |
| cs-im Service | +4 (TicketService, FaqService, VideoReplayService, OfflineMessageStore) + 重写 MessageService + SessionService + RobotEngine |
| cs-im Controller | +3 (TicketController, FaqController, VideoReplayController) + 重写 MessageController + CustomerController + AgentController + RobotController + ProductController + RiskController + FinancialOrderController |
| cs-im Mapper XML | +4 (TicketMapper, TicketReplyMapper, FaqMapper, FaqCategoryMapper) |
| Flyway | +1 (V3.0.0 video_replay + ticket + faq) |
| cs-frontend | +3 (KnowledgeBase, TicketPanel, ReplayPanel) + 重写 Customer.vue |
| PWA | +1 manifest + 1 sw.js |
| 注释 | 每个新 Java / Vue 文件每行注释 |
| 测试 | +1 (TicketIntegrationTest 4 cases) |

测试总数：51/53 = 96% PASS。