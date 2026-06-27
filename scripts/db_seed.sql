-- =====================================================
-- OnlineChat 种子数据 (v2.2.41)
-- =====================================================
-- 包含:
--   1. 测试账号: 5 客户 + 5 坐席 + 1 管理员 (密码统一 pass123)
--   2. 知识库 FAQ 5 条
--   3. 金融产品 3 个
--   4. KYC / 工单 / 持仓 / 离线消息演示数据
-- =====================================================
--
-- ⚠️ 密码 hash 用 PBKDF2-HMAC-SHA256 (100k iterations) 生成
-- 每个用户独立 salt, 格式: pbkdf2$<iter>$<saltHex>$<hashHex>
-- 与 cs-common CryptoUtils.hashPassword() 输出格式完全一致
-- 重新生成: python3 scripts/gen_password_hash.py
--
-- 演示账号:
--   客户 (customerId c-xxx): demo / customer001-004  密码: pass123
--   坐席 (customerId a-xxx): agent001-004 / supervisor  密码: pass123
--   管理员: admin / admin123 (AuthService 硬编码)
-- =====================================================

USE cs_auth;

-- ========== 客户测试账号 ==========
INSERT IGNORE INTO wechat_user
  (customer_id, openid, nickname, username, password_hash, role, channel, phone_verified, login_fail_count, status, created_at, updated_at)
VALUES
  ('c-demo',        'oa-mock-demo',       '演示用户',  'demo',        'pbkdf2$100000$dba53cc5a5f2eb51e91b249b9a295058$9009e378e624d9744c45f05cdbed1950c8880b71ebe50662b8ffea088527cf44', 'CUSTOMER', 'OA', 1, 0, 1, NOW(), NOW()),
  ('c-customer001', 'oa-mock-customer001','客户001',   'customer001', 'pbkdf2$100000$25b0c3b95ef3eb228077209e40911070$3038db74069615d4838a226e646dab4292dbcf57503c00c2e64cc40106c8f2d6', 'CUSTOMER', 'OA', 1, 0, 1, NOW(), NOW()),
  ('c-customer002', 'oa-mock-customer002','客户002',   'customer002', 'pbkdf2$100000$790def537704535fc36caf85846063fc$10b6104846ced7fc4e74e00eb4c6aa3556840c748fa366d5a1078275d946b3e4', 'CUSTOMER', 'OA', 1, 0, 1, NOW(), NOW()),
  ('c-customer003', 'oa-mock-customer003','客户003',   'customer003', 'pbkdf2$100000$0c9b3c97bfb6c25d46c6c7dd56ac6bd3$f1af5632ba393936fe3463429046d9a30a01aadb576037651e938d3b963161e9', 'CUSTOMER', 'OA', 0, 0, 1, NOW(), NOW()),
  ('c-customer004', 'oa-mock-customer004','客户004',   'customer004', 'pbkdf2$100000$a726944a8515ab2a58a9b95baea3af10$e1b69b361d892c646fe486d9bfcd7d484c5f71acb33e746b61860798136f1ba8', 'CUSTOMER', 'OA', 1, 0, 1, NOW(), NOW());

-- ========== 坐席测试账号 ==========
INSERT IGNORE INTO wechat_user
  (customer_id, openid, nickname, username, password_hash, role, channel, phone_verified, login_fail_count, status, created_at, updated_at)
VALUES
  ('a-agent001', 'oa-mock-agent001', '坐席小张', 'agent001', 'pbkdf2$100000$15bef28195dec9215066779794272c9b$d93ec852118ad7d2cf6b4928b60d844417496da69cb6e8d7734d7427085621f0', 'AGENT', 'WORK', 1, 0, 1, NOW(), NOW()),
  ('a-agent002', 'oa-mock-agent002', '坐席小李', 'agent002', 'pbkdf2$100000$59f54ad7c78240d2ad69cf66c8e3498b$a6ff946fb599d560ad1dbbfaa94bfe7227f8e386ca501cf9b72632acc8c8ccc9', 'AGENT', 'WORK', 1, 0, 1, NOW(), NOW()),
  ('a-agent003', 'oa-mock-agent003', '坐席小王', 'agent003', 'pbkdf2$100000$3ea4cbe377d8d6cf2f0f938476548c22$c40061bcbecbdb3b1738f64f324842c7d5f49fbc1c630eb148a0ba39f4ad8021', 'AGENT', 'WORK', 1, 0, 1, NOW(), NOW()),
  ('a-agent004', 'oa-mock-agent004', '坐席小赵', 'agent004', 'pbkdf2$100000$0522fa8789ef28a9a50b5050dc048d94$9448f065089601df9623b68eb9fdbbca3ce421e18198e8e8e749f38b8fad7b3e', 'AGENT', 'WORK', 1, 0, 1, NOW(), NOW()),
  ('a-supervisor','oa-mock-supervisor','主管小陈','supervisor','pbkdf2$100000$1dfe3b0570609cfddd1e28476b891415$9ddcc4974b09af45493be1b79c2b1fda407d65b9fd0a2812cea2410f4041cfb5', 'AGENT', 'WORK', 1, 0, 1, NOW(), NOW());

-- 管理员 (admin/admin123) 是 AuthService 硬编码，不入库

-- ========== cs_im 聊天会话和消息 ==========
USE cs_im;

INSERT IGNORE INTO chat_session
  (id, customer_id, agent_username, status, queued_at, last_active_at, created_at, updated_at)
VALUES
  (1, 'c-demo',       'agent001', 'OPEN',   NOW() - INTERVAL 30 MINUTE, NOW() - INTERVAL 26 MINUTE, NOW() - INTERVAL 30 MINUTE, NOW()),
  (2, 'c-customer001','agent001', 'CLOSED', NOW() - INTERVAL 3 HOUR,    NOW() - INTERVAL 1 HOUR,    NOW() - INTERVAL 3 HOUR,    NOW()),
  (3, 'c-customer002','agent002', 'OPEN',   NOW() - INTERVAL 40 MINUTE, NOW() - INTERVAL 30 MINUTE, NOW() - INTERVAL 40 MINUTE, NOW()),
  (4, 'c-customer003', NULL,       'WAITING',NOW() - INTERVAL 5 MINUTE,  NOW() - INTERVAL 5 MINUTE,  NOW() - INTERVAL 5 MINUTE,  NOW()),
  (5, 'c-customer004','agent003', 'OPEN',   NOW() - INTERVAL 15 MINUTE, NOW() - INTERVAL 10 MINUTE, NOW() - INTERVAL 15 MINUTE, NOW());

INSERT IGNORE INTO chat_message
  (id, session_id, from_user, from_role, type, content, created_at)
VALUES
  -- 会话 1: c-demo ↔ agent001 (5 条)
  (1, 1, 'c-demo',    'CUSTOMER', 'TEXT', '你好', NOW() - INTERVAL 30 MINUTE),
  (2, 1, 'agent001',  'AGENT',    'TEXT', '您好，请问有什么可以帮您？', NOW() - INTERVAL 29 MINUTE),
  (3, 1, 'c-demo',    'CUSTOMER', 'TEXT', '我想了解理财产品', NOW() - INTERVAL 28 MINUTE),
  (4, 1, 'agent001',  'AGENT',    'TEXT', '好的，请问您偏好哪种类型？稳健型还是进取型？', NOW() - INTERVAL 27 MINUTE),
  (5, 1, 'c-demo',    'CUSTOMER', 'TEXT', '稳健型吧', NOW() - INTERVAL 26 MINUTE),
  -- 会话 2: c-customer001 ↔ agent001 (4 条)
  (6, 2, 'c-customer001','CUSTOMER','TEXT', '银行卡绑定有问题', NOW() - INTERVAL 3 HOUR),
  (7, 2, 'agent001',  'AGENT',    'TEXT', '请提供您的卡号后4位', NOW() - INTERVAL 2 HOUR),
  (8, 2, 'c-customer001','CUSTOMER','TEXT', '1234', NOW() - INTERVAL 2 HOUR),
  (9, 2, 'agent001',  'AGENT',    'TEXT', '工单已处理完成，感谢您的耐心', NOW() - INTERVAL 1 HOUR),
  -- 会话 3: c-customer002 ↔ agent002 (3 条)
  (10, 3, 'c-customer002','CUSTOMER','TEXT', '基金赎回需要几天？', NOW() - INTERVAL 40 MINUTE),
  (11, 3, 'agent002',  'AGENT',    'TEXT', '工作日 T+1 到账', NOW() - INTERVAL 35 MINUTE),
  (12, 3, 'c-customer002','CUSTOMER','TEXT', '好的谢谢', NOW() - INTERVAL 30 MINUTE),
  -- 会话 4: c-customer003 排队 (1 条)
  (13, 4, 'c-customer003','CUSTOMER','TEXT', '在吗？', NOW() - INTERVAL 5 MINUTE),
  -- 会话 5: c-customer004 ↔ agent003 (5 条)
  (14, 5, 'c-customer004','CUSTOMER','TEXT', '理财', NOW() - INTERVAL 15 MINUTE),
  (15, 5, 'agent003',  'AGENT',    'TEXT', '欢迎咨询，请问预算多少？', NOW() - INTERVAL 14 MINUTE),
  (16, 5, 'c-customer004','CUSTOMER','TEXT', '5万', NOW() - INTERVAL 13 MINUTE),
  (17, 5, 'agent003',  'AGENT',    'TEXT', '为您推荐稳健型组合，预期年化 4-5%', NOW() - INTERVAL 12 MINUTE),
  (18, 5, 'c-customer004','CUSTOMER','TEXT', '好的', NOW() - INTERVAL 10 MINUTE);

-- ========== FAQ 知识库 ==========
INSERT IGNORE INTO faq_category
  (id, name, parent_id, sort_order, icon, created_at)
VALUES
  (1, '开户', NULL, 1, NULL, NOW()),
  (2, '产品', NULL, 2, NULL, NOW()),
  (3, '交易', NULL, 3, NULL, NOW()),
  (4, '账户', NULL, 4, NULL, NOW()),
  (5, '客服', NULL, 5, NULL, NOW());

INSERT IGNORE INTO faq
  (id, category_id, question, answer, keywords, view_count, helpful_count, status, created_at, updated_at)
VALUES
  (1, 1, '如何开户？', '您可以通过微信公众号菜单"在线开户"，填写个人信息后即可完成开户流程，整个过程约需3分钟。', '开户 注册 流程', 152, 50, 'ACTIVE', NOW(), NOW()),
  (2, 2, '有哪些理财产品？', '我们提供稳健型、平衡型、进取型三类理财产品，预期年化收益率分别为 3-4%、4-5%、6-8%，适合不同风险偏好。', '理财 产品 类型 收益', 89, 30, 'ACTIVE', NOW(), NOW()),
  (3, 3, '基金赎回需要几天？', '工作日 T+1 到账，即下一个工作日到账，遇法定节假日顺延。', '基金 赎回 到账', 234, 80, 'ACTIVE', NOW(), NOW()),
  (4, 4, '忘记密码怎么办？', '在登录页面点击"忘记密码"，通过手机验证码即可重置密码。', '密码 忘记 重置', 67, 25, 'ACTIVE', NOW(), NOW()),
  (5, 5, '客服工作时间？', '在线客服 7x24 小时全天候服务，电话客服 9:00-21:00。', '客服 工作时间 联系', 312, 100, 'ACTIVE', NOW(), NOW());

-- ========== 金融产品 ==========
INSERT IGNORE INTO product
  (id, product_code, name, description, product_type, risk_level, yield_rate, period, min_amount, max_amount, status, created_at, updated_at)
VALUES
  (1, 'P-W001', '稳健理财 1 号', '稳健型理财，预期年化 3-4%，适合保守投资者', 'WEN_SHAN', 'LOW',    0.0350, '90天',  10000,  1000000, 'ON_SALE', NOW(), NOW()),
  (2, 'P-B001', '平衡理财 1 号', '平衡型理财，预期年化 4-5%，适合稳健投资者', 'PING_HENG','MEDIUM', 0.0450, '180天', 50000,  5000000, 'ON_SALE', NOW(), NOW()),
  (3, 'P-A001', '进取理财 1 号', '进取型理财，预期年化 6-8%，适合激进投资者', 'JIN_QU',   'HIGH',   0.0700, '365天', 100000, 10000000,'ON_SALE', NOW(), NOW());

-- ========== 工单 ==========
INSERT IGNORE INTO ticket
  (id, ticket_no, customer_id, agent_username, title, description, category, priority, status, sla_deadline, created_at, updated_at, resolved_at)
VALUES
  (1, 'T20260627001', 'c-demo',        'agent001', '银行卡绑定失败', '绑定招行卡时提示错误',     '账户', 'HIGH',   'OPEN',   NOW() + INTERVAL 4 HOUR, NOW() - INTERVAL 2 DAY, NOW(), NULL),
  (2, 'T20260627002', 'c-customer001', 'agent001', '修改预留手机号', '想换手机号',           '账户', 'NORMAL', 'CLOSED', NULL,                    NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 6 HOUR),
  (3, 'T20260627003', 'c-customer002', 'agent002', '基金收益查询',   '近一月收益多少？',     '产品', 'LOW',    'OPEN',   NOW() + INTERVAL 24 HOUR, NOW() - INTERVAL 6 HOUR, NOW(), NULL);

-- ========== 持仓 ==========
INSERT IGNORE INTO holding
  (id, customer_id, product_code, principal, accumulated_income, status, start_date, end_date, created_at, updated_at)
VALUES
  (1, 'c-customer004', 'P-W001', 50000.00, 1500.00, 'HOLDING', NOW() - INTERVAL 30 DAY, NOW() + INTERVAL 60 DAY, NOW() - INTERVAL 30 DAY, NOW()),
  (2, 'c-customer004', 'P-B001', 80000.00, 3600.00, 'HOLDING', NOW() - INTERVAL 60 DAY, NOW() + INTERVAL 120 DAY, NOW() - INTERVAL 60 DAY, NOW());

-- ========== cs_message 离线消息 ==========
USE cs_message;

INSERT IGNORE INTO offline_message
  (id, user_id, msg_id, session_id, sender_id, msg_type, payload, delivered, expires_at, created_at)
VALUES
  (1, 'c-demo',        'seed-msg-001', 1, 'agent001',  'TEXT', '{"text":"坐席已回复您的咨询"}', 0, NOW() + INTERVAL 7 DAY, NOW() - INTERVAL 30 MINUTE),
  (2, 'c-customer001', 'seed-msg-002', 2, 'agent001',  'TEXT', '{"text":"工单已处理完成"}',     0, NOW() + INTERVAL 7 DAY, NOW() - INTERVAL 1 HOUR),
  (3, 'c-customer004', 'seed-msg-003', 5, 'agent003',  'TEXT', '{"text":"为您推荐理财组合"}',   0, NOW() + INTERVAL 7 DAY, NOW() - INTERVAL 10 MINUTE);

-- ========== 统计 ==========
SELECT '=== 账号 ===' AS section;
SELECT
  CASE WHEN customer_id LIKE 'a-%' THEN '坐席' ELSE '客户' END AS role_type,
  customer_id, nickname, username, role, channel
FROM cs_auth.wechat_user
ORDER BY customer_id;

SELECT '=== 聊天会话 ===' AS section;
SELECT id, customer_id, agent_username, status, last_active_at
FROM cs_im.chat_session ORDER BY id;

SELECT '=== 聊天消息 (前 10 条) ===' AS section;
SELECT session_id, from_user, from_role, LEFT(content, 30) AS preview, created_at
FROM cs_im.chat_message ORDER BY id LIMIT 10;

SELECT '=== FAQ ===' AS section;
SELECT id, category_id, question FROM cs_im.faq ORDER BY id;

SELECT '=== 金融产品 ===' AS section;
SELECT product_code, name, risk_level, yield_rate FROM cs_im.product;

SELECT '=== 工单 ===' AS section;
SELECT ticket_no, customer_id, title, status, priority FROM cs_im.ticket;

SELECT '=== 持仓 ===' AS section;
SELECT customer_id, product_code, principal, accumulated_income FROM cs_im.holding;

SELECT '=== 离线消息 ===' AS section;
SELECT id, user_id, msg_type, delivered FROM cs_message.offline_message;

SELECT '✅ 数据初始化完成' AS done;
SELECT
  (SELECT COUNT(*) FROM cs_auth.wechat_user)     AS users,
  (SELECT COUNT(*) FROM cs_im.chat_session)      AS sessions,
  (SELECT COUNT(*) FROM cs_im.chat_message)      AS messages,
  (SELECT COUNT(*) FROM cs_im.faq)               AS faqs,
  (SELECT COUNT(*) FROM cs_im.product)           AS products,
  (SELECT COUNT(*) FROM cs_im.ticket)            AS tickets,
  (SELECT COUNT(*) FROM cs_im.holding)            AS holdings,
  (SELECT COUNT(*) FROM cs_message.offline_message) AS offline_msgs;