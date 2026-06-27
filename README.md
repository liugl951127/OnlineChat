# 在线客服系统（Spring Cloud 微服务版）

基于 Spring Cloud Alibaba 的在线客服系统，支持**公众号 + 企业微信双 OAuth**、**机器人默认应答**、**富文本消息**、**离线消息推送**、**历史回溯**、**合规审计**。

## 架构

```
┌──────────┐    ┌──────────┐    ┌──────────┐
│ 客户 H5  │    │ 坐席 H5  │    │ 后管 H5  │
└────┬─────┘    └────┬─────┘    └────┬─────┘
     │               │               │
     ▼               ▼               ▼
┌────────────────────────────────────────────┐
│         cs-gateway (Spring Cloud Gateway)  │
│  • JWT 鉴权                                │
│  • 路由分发 (Nacos Discovery)              │
└────┬───────────┬───────────┬───────────────┘
     │           │           │
     ▼           ▼           ▼
┌────────┐  ┌─────────────────┐  ┌─────────┐
│cs-auth │  │     cs-im       │  │cs-message│
│OAuth/JWT│  │ (IM+机器人+金融) │  │Kafka消费 │
└────────┘  └─────────────────┘  └─────────┘
                  │
                  └─── Kafka ──┘

数据层:
  MySQL 8.4（3 库：auth/im/trade，MyBatis Plus 3.5.7）
  Redis 7（在线标记 + 离线消息）
  Kafka 3.7（异步消息 + 事件溯源）

数据层:
  MySQL（5 库：auth/robot/im/trade/none）
  Redis（在线标记 + 离线消息 Stream）
  Nacos（注册中心 + 配置中心）
```

## 业务模块

| 模块 | 端口 | 职责 |
|---|---|---|
| cs-gateway | 9000 | 统一入口、JWT 鉴权、静态资源 |
| cs-auth | 9001 | 公众号/企微 OAuth + 静默登录 + JWT |
| cs-robot | 9002 | 关键词意图 + 富文本构造 + LLM 兜底 |
| cs-im | 9003 | WebSocket(STOMP) + 会话状态机 + 离线推送 |
| cs-trade | 9004 | 交易 + 联网核查 + 账单 + 产品 |

## 快速开始（开发模式）

### 1. 启动基础设施
```bash
# 启动 Nacos（standalone）
docker run -d --name nacos -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone nacos/nacos-server:v2.3.2

# 启动 Redis
docker run -d --name redis -p 6379:6379 redis:7
```

### 2. 启动微服务
```bash
mvn clean package -DskipTests

# 启动顺序：auth → robot/trade → im → gateway
cd cs-auth && mvn spring-boot:run &
cd cs-robot && mvn spring-boot:run &
cd cs-trade && mvn spring-boot:run &
cd cs-im && mvn spring-boot:run &
cd cs-gateway && mvn spring-boot:run &
```

### 3. 访问入口

- 引导页: http://127.0.0.1:9000/
- 客户 H5: http://127.0.0.1:9000/customer/
- 坐席工作台: http://127.0.0.1:9000/agent/
- 后管系统: http://127.0.0.1:9000/admin/  (admin/admin)

## 富文本消息协议

支持 5 种类型，详见 `cs-common` 的 `RichMessage`：

```java
RichMessage.text("纯文本");
RichMessage.bill("最近7天账单", List<Map>);
RichMessage.product("活期理财", "稳健", 2.85, "灵活");
RichMessage.card("标题", "描述", imageUrl, linkUrl);
RichMessage.chart("趋势图", labels, data);
```

## 离线推送

客户关闭页面/杀进程后：
1. WS 断开 → `online:{cid}` 标记删除
2. 后续机器人/坐席消息 → 写入 Redis Stream `offline-msg`
3. 定时任务（30s）扫描 Stream → 调企业微信应用消息 / 公众号模板消息
4. 客户重新进入 → 标记在线 → 停止推送

**生产配置**（application.yml）：
```yaml
wechat:
  oa: { app-id: xxx, app-secret: xxx, mock: false }
  work: { corp-id: xxx, agent-id: xxx, app-secret: xxx, mock: false }
```

## 合规化（等保 2.0）

| 要求 | 实现 |
|---|---|
| 身份鉴别 | 公众号 OAuth + 静默登录 + 企微 OAuth + JWT |
| 访问控制 | `@requireRole("ADMIN")` + JWT 角色字段 |
| 审计日志 | `audit_log` 表 + `AuditService` 异步写库 |
| 数据脱敏 | `SensitiveUtils.maskMobile/IdCard/Name` |
| 敏感操作二次确认 | 强制挂断必填原因 |
| 通信保密 | 生产建议 Nginx HTTPS |

## 测试

```bash
mvn test
```

覆盖：
- cs-auth: 静默登录、OAuth、管理员登录（4 用例）
- cs-im: E2E 会话生命周期、转人工、强制挂断、历史、统计（6 用例）
- cs-trade: 数据 seed、产品搜索（2 用例）

## 功能验证图

详见 [docs/01-FLOW-VERIFICATION.md](docs/01-FLOW-VERIFICATION.md)：
- 8 个 E2E 时序图
- 接口覆盖清单（30+ 端点）
- 合规化 checklist

## 业务模块划分原则

按"业务域"划分，不按技术层划分：
- 不会拆出 cs-common-controller / cs-common-service
- 数据库按业务切分（每服务一库），不强求分布式事务（业务降级为最终一致性）
- FeignClient 用于跨服务调用，OpenFeign + Sentinel 限流（预留）

## 待办 / 后续

- [ ] 接入真实 Nacos 配置中心
- [ ] 接入 SkyWalking 全链路追踪
- [ ] LLM 兜底（cs-robot 留接口）
- [ ] 分布式事务（Seata）处理"交易成功但消息未广播"边界
- [ ] 灰度发布（金丝雀）