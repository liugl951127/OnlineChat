# 微服务架构演进（v1.0 → v1.8.0）

## 1. 演进时间线

| 版本 | 微服务数 | 主要变化 |
|------|----------|----------|
| v1.0-v1.5 | 7 | cs-gateway / cs-auth / cs-im / cs-robot / cs-trade / cs-message / cs-common |
| v1.6.5 | 7 | 加 OAuth 第三方登录 |
| v1.7.0 | 7 | 前端 SPA + 10 大类安全 Filter |
| v1.7.1 | 7 | H2 → MySQL 8 + Druid + Flyway |
| v1.7.2 | 7 | Kafka 异步消息 + 离线消息 |
| **v1.8.0** | **5** | **JPA → MyBatis Plus + cs-trade + cs-robot 合并到 cs-im + 金融产品合规化** |

## 2. v1.8.0 架构图

```
                     ┌──────────────────────┐
                     │   Vue 3 Frontend      │
                     │  (Element Plus 2.7)   │
                     └──────────┬───────────┘
                                │ HTTP (9000)
                     ┌──────────▼───────────┐
                     │    cs-gateway         │
                     │  (Spring Cloud GW)    │
                     │  - 静态资源 (前端)    │
                     │  - JWT 透传           │
                     │  - 安全 Filter 链     │
                     └────┬─────┬─────┬──────┘
                          │     │     │
              ┌───────────┘     │     └────────────┐
              ▼                 ▼                  ▼
       ┌────────────┐    ┌────────────┐     ┌────────────┐
       │  cs-auth   │    │   cs-im    │     │ cs-message │
       │  (9001)    │    │   (9003)   │     │  (9005)    │
       │            │    │            │     │            │
       │ - 微信登录 │    │ - IM 聊天   │     │ - Kafka    │
       │ - OAuth    │    │ - 机器人    │     │   消费者   │
       │ - 用户名密码│   │ - 金融订单  │     │ - 离线消息 │
       │ - JWT 签发 │    │ - 合规检查  │     │ - 坐席路由 │
       │ - /verify/ │    │ - 风险评估  │     │            │
       │    phone   │    │ - 持仓      │     │            │
       └────────────┘    └─────┬──────┘     └────────────┘
                              │
                              │ Kafka (异步消息)
                              │
                       ┌──────▼───────┐
                       │    Kafka     │
                       │   3.7 KRaft  │
                       └──────────────┘

共享组件：cs-common
  - MyBatis Plus BaseEntity + 自动填充
  - KafkaTopics + KafkaMessageProducer + ChatMessageEvent
  - Security Filters 5 个 (Headers/CSRF/SQL Inj/File Upload/Replay)
  - Sanitizer / RateLimiter / CryptoUtils
  - DTO / 异常 / ApiResponse / SecurityContext
```

## 3. 微服务合并决策

### 合并 cs-trade → cs-im
**原因**：
- cs-trade 只有 5 个 Java 文件 (domain/repo/service/controller × Product + Bill)
- cs-trade 与 IM 业务紧密耦合（购买流程要在 IM 流中触发）
- 减少 Nacos 注册数 + 节省 Docker 容器开销

**实施**：
- 把 cs-trade/src/main/java/com/example/trade/* 复制到 cs-im/src/main/java/com/example/im/
- 包名重命名 `com.example.trade` → `com.example.im`
- 父 pom `<modules>` 删除 cs-trade
- docker-compose.yml 删除 cs-trade service
- deploy/docker/ 删除 cs-trade.Dockerfile

### 合并 cs-robot → cs-im
**原因**：
- cs-robot 只有 3 个 Java 文件 (RobotEngine + 2 个 controller)
- 机器人应答是 IM 会话的子状态
- 客户触发 "转人工" 时机器人与 IM Session 状态共享

**实施**：同上

### 合并后的 cs-im 职责
1. **IM 聊天**（消息收发、会话生命周期）
2. **机器人应答**（关键词匹配 + 富文本回复）
3. **金融交易**（产品列表 + 订单 + 支付 + 持仓 + 赎回）

## 4. ORM 演进：JPA → MyBatis Plus

### 选型原因

| 维度 | JPA | MyBatis Plus |
|------|-----|--------------|
| 学习成本 | 高（实体关系映射、懒加载） | 低（SQL 直观） |
| 复杂查询 | 难（JPQL/Criteria） | 易（XML + QueryWrapper） |
| 分页 | 自动（Pageable） | 内置 PaginationInnerInterceptor |
| 性能 | 反射开销大 | 半自动映射，灵活 |
| 动态 SQL | Specification 复杂 | QueryWrapper / Lambda 简单 |
| 多租户/分库分表 | 中等支持 | 插件完善（tenant + sharding） |

选 MyBatis Plus 的关键原因：**cs-trade 和 cs-im 有大量动态查询（按状态/类型/日期筛选），JPA 写起来很啰嗦**。

### 实施细节
```java
// BaseEntity 公共基类
@Data
public abstract class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}

// 自动填充 Handler
public class MybatisAutoFillHandler implements MetaObjectHandler {
    public void insertFill(MetaObject m) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(m, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(m, "updatedAt", LocalDateTime.class, now);
    }
    public void updateFill(MetaObject m) {
        this.strictUpdateFill(m, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}

// 仓储层包装（保持 JPA-like 语义）
@Repository
public class ChatSessionRepo {
    @Autowired private ChatSessionMapper mapper;  // BaseMapper<ChatSession>
    public ChatSession save(ChatSession s) { mapper.insert(s); return s; }
    public Optional<ChatSession> findById(Long id) { return Optional.ofNullable(mapper.selectById(id)); }
    public List<ChatSession> findActiveByCustomer(String customerId) {
        return mapper.findActiveByCustomer(customerId);  // 自定义 XML
    }
}
```

## 5. 测试矩阵（v1.8.0）

```
cs-common:
  SecurityFilterTest .......................... 14/14 PASS

cs-auth:
  AuthEndToEndTest ............................ 4/4 PASS
  AuthLoginTest ............................... 10/12 PASS (2 旧 lockFlush 问题遗留)
  AuthMysqlIntegrationTest .................... 5/5 PASS  (新加 /auth/verify/phone 测试)
  OAuthLoginTest .............................. 9/9 PASS

cs-im:
  FinancialOrderIntegrationTest ................ 5/5 PASS  (新加)

cs-message: 暂未集成测试
cs-gateway:  静态资源 + 路由，集成测在 docker-compose 启动后做

总计: 47/49 = 96%
```

## 6. 关键技术决策

| 决策 | 选型 | 理由 |
|------|------|------|
| 前端框架 | Vue 3 + Element Plus 2.7 | 中文生态、组件丰富、TypeScript 可选 |
| 网关 | Spring Cloud Gateway | 响应式、高性能、与 Spring Cloud 集成 |
| 注册中心 | Nacos 2.3.2 | 一体化（注册 + 配置），中文文档 |
| 消息中间件 | Kafka 3.7 KRaft | 无 ZK 依赖、3 分区足够 |
| ORM | MyBatis Plus 3.5.7 | 灵活 SQL + 半自动映射 |
| 数据库 | MySQL 8.4.0 (生产) / H2 (单测) | 主流 + Flyway 兼容 |
| 缓存 | Redis 7.2 | 离线消息 + 会话状态 |
| 安全 | Spring Security + 5 自研 Filter | CSP/CSRF/SQL Inj/Replay/File |
| 部署 | Docker Compose (5 服务) + Nginx | 一件启动 |
| Java | 17 LTS | records/sealed/text blocks |

## 7. 已知遗留问题

1. **AuthLoginTest 2 个 lockUntil 测试失败**：`@Transactional` flush 时机问题，需用 `flush()` 强制同步
2. **cs-message / cs-gateway 无 SpringBootTest**：通过 docker-compose 启动后做 smoke test
3. **生产 HTTPS 终结在 Nginx**：Java 服务只处理 HTTP，TLS 在反向代理层
4. **mock 模式 fallback**：UserVerifyService 当 cs-auth 调用失败时，`customerId.startsWith("real_")` 视为已认证（仅用于演示）