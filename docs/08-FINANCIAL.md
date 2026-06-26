# 金融产品与合规化（v1.8.0）

cs-im 微服务集成金融产品购买流程，覆盖保险/理财/基金/国债四大类，合规化检查（实名认证、风险评估、适当性匹配、反洗钱），
支持客户自助购买与坐席协助下单两种模式。

## 1. 业务架构

```
┌─────────────────┐    购买请求     ┌──────────────────┐
│ Customer Vue    │ ───────────────► │                  │
└─────────────────┘                  │                  │
┌─────────────────┐    协助下单      │  cs-im (合并)     │
│ Agent Vue       │ ───────────────► │                  │
└─────────────────┘                  │  - Product       │
                                     │  - RiskAssess    │
                                     │  - Compliance    │
                                     │  - FinancialOrder│
                                     │  - Holding       │
                                     │      ↓           │
                                     │  Kafka           │
                                     └────────┬─────────┘
                                              ↓
                                     ┌──────────────────┐
                                     │  cs-auth         │
                                     │  /auth/verify/   │
                                     │     phone        │
                                     └──────────────────┘
```

## 2. 微服务合并（v1.8.0）

v1.8.0 把 cs-trade + cs-robot 合并到 cs-im，减少微服务数量从 7 → 5（cs-gateway / cs-auth / cs-im / cs-message / cs-common），
cs-im 现在承担 **IM 聊天 + 机器人 + 金融交易** 三大职责。

理由：
- cs-trade 只有 5 个 Java 文件
- cs-robot 只有 3 个 Java 文件
- IM/机器人/金融三者紧密耦合（机器人要推送产品，金融要在 IM 流中触发订单）

## 3. ORM 切换：JPA → MyBatis Plus

v1.7.x 用 spring-data-jpa，v1.8.0 切换为 **MyBatis Plus 3.5.7**：
- `BaseMapper<T>` 代替 `JpaRepository<T, Long>`
- `MetaObjectHandler` 自动填充 createdAt/updatedAt
- `@TableLogic` 逻辑删除（`deleted` 字段 0/1）
- `PaginationInnerInterceptor` + `OptimisticLockerInnerInterceptor`
- `application.yml` 中开启 `map-underscore-to-camel-case: true`

依赖：
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
```

## 4. 数据库表（Flyway V2.0.0）

新增 5 张金融表（在 V1.0.0 cs_im schema 基础上 ALTER + CREATE）：

```sql
-- product 表扩展金融字段
ALTER TABLE product ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT 'DEPOSIT';
ALTER TABLE product ADD COLUMN risk_level   VARCHAR(16) NOT NULL DEFAULT 'LOW';
ALTER TABLE product ADD COLUMN yield_rate   DECIMAL(8, 4) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN period       VARCHAR(16) NOT NULL DEFAULT 'PERPETUAL';
ALTER TABLE product ADD COLUMN min_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN max_amount   DECIMAL(18, 2) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'ON_SALE';
ALTER TABLE product ADD COLUMN is_deleted   TINYINT(1) NOT NULL DEFAULT 0;

-- 金融订单
CREATE TABLE financial_order (
    id, order_no, customer_id, agent_username, product_code, product_name,
    amount, payment_method, status (DRAFT/RISK_ASSESSED/COMPLIANCE_PASSED/PAYING/SETTLED/REDEEMED/REJECTED),
    risk_level, risk_score, compliance_result, compliance_remark,
    initiator_role (CUSTOMER/AGENT), paid_at, completed_at, redeemed_at, ...

-- 风险评估
CREATE TABLE risk_assessment (
    id, customer_id, score (0-100), risk_level (CONSERVATIVE/MODERATE/AGGRESSIVE),
    answers_json, expires_at, ...

-- 合规检查
CREATE TABLE compliance_check (
    id, order_no,
    identity_check (TINYINT), risk_check, suitability_check, aml_check,
    overall_result (PASS/REJECTED), remark, ...

-- 持仓
CREATE TABLE holding (
    id, customer_id, product_code, principal, accumulated_income,
    status (HOLDING/REDEEMED), start_date, end_date, ...
)
```

5 个金融产品演示数据：

| code | name | product_type | risk_level | yield_rate | period | min_amount |
|------|------|--------------|-----------|-----------|--------|-----------|
| PRD-INS-001 | 平安出行险 | INSURANCE | LOW | 0% | 1Y | 50 |
| PRD-DEP-001 | 稳赢 30 天 | DEPOSIT | LOW | 2.8% | 30D | 100 |
| PRD-DEP-002 | 稳赢 90 天 | DEPOSIT | MID | 3.5% | 90D | 1000 |
| PRD-FND-001 | 成长基金 | FUND | HIGH | 浮动 | PERPETUAL | 1000 |
| PRD-BND-001 | 国债 5 年 | BOND | LOW | 3.2% | 5Y | 1000 |

## 5. 风险评估问卷（5 题加权）

| 题目 | 选项（0/1/2） | 权重 |
|------|------|------|
| 年龄 | 18-30 / 31-50 / 51+ | 10% |
| 年收入 | <10万 / 10-50万 / >50万 | 20% |
| 投资经验 | 无 / 1-3年 / >3年 | 20% |
| 风险偏好 | 保本 / 稳健 / 激进 | 30% |
| 资产占比 | <10% / 10-30% / >30% | 20% |

总分 0-100 → 风险等级：
- **CONSERVATIVE**（保守型）：0-39 分
- **MODERATE**（稳健型）：40-69 分
- **AGGRESSIVE**（激进型）：70-100 分

评估结果有效期 **1 年**，写入 risk_assessment 表。

## 6. 合规 4 道关

每笔订单必须依次通过：

| 关卡 | 内容 | 数据来源 |
|------|------|----------|
| 1️⃣ 实名认证 | `customer.phoneVerified = 1` | `cs-auth` → `GET /auth/verify/phone?customerId=xxx` |
| 2️⃣ 风险评估 | 存在未过期的 risk_assessment 记录 | cs-im risk_assessment 表 |
| 3️⃣ 适当性匹配 | 客户风险等级 ≥ 产品风险等级 (1<2<3) | 本地比较 |
| 4️⃣ 反洗钱 (AML) | 单笔 ≤ 5万 / 单日累计 ≤ 20万 | 本地累计 |

任一关卡失败 → `compliance_check.overall_result = REJECTED` + 写入 `remark`（如 "未实名认证; 单笔金额超过 5 万限额"）。

## 7. 5 步状态机

```
createOrder       DRAFT ──────────────────────────────────┐
       │                                                  │
       ▼                                                  │
assessRisk       RISK_ASSESSED ───────────────────────────┤
       │                                                  │
       ▼                                                  │
runCompliance    COMPLIANCE_PASSED  (或 REJECTED) ────────┤
       │                                                  │
       ▼                                                  │
pay              PAYING → SETTLED  (生成 Holding 记录)    │
       │                                                  │
       ▼                                                  │
redeem           REDEEMED  (更新 Holding.status=REDEEMED) ┘
```

API：
- `POST /order/create` 创建订单
- `POST /order/{orderNo}/assess` 风险评估
- `POST /order/{orderNo}/compliance` 合规检查
- `POST /order/{orderNo}/pay` 支付（mock）
- `POST /order/{orderNo}/redeem` 赎回
- `POST /order/one-click-buy` 一键购买（评估→订单→评估→合规→支付 复合方法）

## 8. 一键购买复合方法

`FinancialOrderService.oneClickBuy(customerId, productCode, amount, agentUsername)`：

```
1) 查产品 → 不存在则 404
2) 查最新风险评估 → 不存在则提示先评估（前端弹问卷）
3) 创建订单 DRAFT
4) 风险评估 → RISK_ASSESSED
5) 合规检查 → 任一失败 → REJECTED，返回原因
6) 支付（mock） → SETTLED，生成 Holding
```

返回结构：
```json
{
  "success": true,
  "orderNo": "FO1739123456789",
  "message": "购买成功",
  "complianceRemark": null
}
```

## 9. 坐席协助下单

坐席在 Agent.vue 中点 "🛍️ 金融产品" 按钮 → 弹出 ProductPanel → 选择客户 → 选择产品 → 一键购买。

订单表记录 `agent_username` + `initiator_role = 'AGENT'`，审计时可追溯是谁帮客户下单。

## 10. 测试覆盖

`cs-im/src/test/java/com/example/im/FinancialOrderIntegrationTest.java`：

| 测试 | 内容 |
|------|------|
| testListProducts | 验证 5 个金融产品种子 |
| testRiskAssessmentConservative | 5 个 0 分 → CONSERVATIVE |
| testRiskAssessmentAggressive | 5 个 2 分 → AGGRESSIVE |
| testFullPurchaseFlow | 完整 5 步状态机 DRAFT → SETTLED |
| testAmountBelowMinimum | 金额低于起购额抛异常 |

实际跑通 **5/5 PASS**。

## 11. 前端集成

`cs-frontend/src/components/ProductPanel.vue`：
- 风险评估状态条（已完成 / 未完成）
- 产品卡片网格（按 productType 着色）
- 风险评估问卷弹窗（5 题 ElRadioGroup）
- 购买确认弹窗（金额输入 + 支付方式 + 合规结果反馈）

`Agent.vue` 在工具栏加了 "🛍️ 金融产品购买" 按钮（`showProductPanel` toggle）。
`Customer.vue` 同样可加入口（坐席协助模式下可见）。

## 12. Kafka 集成

购买结果通过 Kafka 异步推送到客户/坐席的会话频道：
- 主题 `CUSTOMER_MESSAGE` / `AGENT_MESSAGE`
- 消息体 `WsEnvelope{type="ORDER_STATUS", orderNo, status, ...}`
- cs-message 服务消费 → WebSocket 推送给前端

## 13. 安全注意事项

- 购买金额校验（起购/限购）必须服务端做，前端只是 UI 限制
- 支付通道一律 mock（MOCK_BANK / WECHAT_PAY / ALIPAY），生产对接银联 / 微信支付
- AML 阈值（5 万 / 20 万）按监管要求，**硬编码** 在 `ComplianceService.AML_SINGLE_LIMIT/AML_DAILY_LIMIT`
- 监管要求：所有金融订单至少保留 5 年