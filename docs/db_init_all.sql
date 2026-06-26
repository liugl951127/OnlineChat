-- =====================================================
-- OnlineChat 一键初始化脚本（MySQL 8.0.46 / MariaDB 10.5+）
-- 合并自 Flyway V1.0.0 ~ V5.0.0
-- 字符集：utf8mb4 / 排序：utf8mb4_unicode_ci / 引擎：InnoDB
-- =====================================================
-- 用法：
--   mysql -uroot -p < db_init_all.sql
--   或在 mysql 客户端里：
--   source /path/to/db_init_all.sql;
-- =====================================================

-- ============================================================
-- 0. 创建数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS cs_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cs_im   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 1. cs-auth 数据库
-- ============================================================
USE cs_auth;

-- ----------------------------------------
-- V1.0.0 用户/Token/审计/黑名单
-- ----------------------------------------

-- =====================================================
-- V1.0.0  cs-auth schema（认证服务）
-- 数据库：cs_auth
-- 字符集：utf8mb4 / 排序：utf8mb4_unicode_ci
-- =====================================================

-- 用户表（合并 WechatUser + LocalUser）
CREATE TABLE IF NOT EXISTS wechat_user (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id         VARCHAR(64)     NOT NULL                COMMENT '业务客户ID',
    username            VARCHAR(64)     NULL                    COMMENT '本地账号',
    password_hash       VARCHAR(256)    NULL                    COMMENT 'PBKDF2 哈希',
    nickname            VARCHAR(64)     NULL                    COMMENT '昵称',
    avatar              VARCHAR(512)    NULL                    COMMENT '头像URL',
    phone_enc           VARCHAR(256)    NULL                    COMMENT '手机号 AES 加密',
    phone_masked        VARCHAR(20)     NULL                    COMMENT '脱敏手机号',
    phone_verified      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '手机是否验证',
    email               VARCHAR(128)    NULL                    COMMENT '邮箱',
    provider            VARCHAR(20)     NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/WECHAT_OA/WECHAT_WORK/GITHUB/GOOGLE',
    provider_user_id    VARCHAR(128)    NULL                    COMMENT 'OAuth 提供方用户ID',
    unionid            VARCHAR(128)    NULL                    COMMENT '微信 unionid',
    login_fail_count    INT             NOT NULL DEFAULT 0,
    lock_until          DATETIME        NULL,
    role                VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER' COMMENT 'CUSTOMER/AGENT/ADMIN',
    status              TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '1=正常 0=禁用',
    last_login_time     DATETIME        NULL,
    last_login_ip       VARCHAR(64)     NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_customer_id (customer_id),
    UNIQUE KEY uk_provider (provider, provider_user_id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone_enc (phone_enc),
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

-- 用户 Token 表（JWT 黑名单 / 强制下线）
CREATE TABLE IF NOT EXISTS user_token (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    token_id        VARCHAR(64)     NOT NULL                COMMENT 'JWT jti',
    issued_at       DATETIME        NOT NULL,
    expires_at      DATETIME        NOT NULL,
    revoked         TINYINT(1)      NOT NULL DEFAULT 0,
    revoke_reason   VARCHAR(64)     NULL,
    ip              VARCHAR(64)     NULL,
    user_agent      VARCHAR(256)    NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_token_id (token_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Token 表';

-- 审计日志
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    operator_id     VARCHAR(64)     NOT NULL,
    operator_name   VARCHAR(64)     NULL,
    action          VARCHAR(64)     NOT NULL                COMMENT 'login/force_hangup/blacklist_add 等',
    target_type     VARCHAR(32)     NULL,
    target_id       VARCHAR(128)    NULL,
    reason          VARCHAR(512)    NULL,
    detail          TEXT            NULL,
    ip              VARCHAR(64)     NULL,
    user_agent      VARCHAR(256)    NULL,
    result          VARCHAR(16)     NOT NULL DEFAULT 'success',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_operator_id (operator_id),
    INDEX idx_action (action),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- 黑名单（IP / 账号）
CREATE TABLE IF NOT EXISTS blacklist (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(16)     NOT NULL                COMMENT 'IP/USER/PHONE',
    value           VARCHAR(128)    NOT NULL,
    reason          VARCHAR(256)    NULL,
    expire_at       DATETIME        NULL                    COMMENT '过期时间，NULL=永久',
    create_by       VARCHAR(64)     NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_type_value (type, value),
    INDEX idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单';

-- 初始管理员账号（密码：admin PBKDF2）
INSERT INTO wechat_user (customer_id, username, password_hash, nickname, role, provider)
VALUES (
    'admin-001',
    'admin',
    'pbkdf2$100000$Y3VycmVudEFkbWluU2FsdA==$...',  -- 占位，运行时由 init 任务覆盖
    '系统管理员',
    'ADMIN',
    'LOCAL'
) ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
-- ----------------------------------------
-- V2.0.0 OAuth openid / ww_userid
-- ----------------------------------------
-- v1.8.0 v2.0.0: add openid/ww_userid columns for OAuth (wechat OA + wechat work)
ALTER TABLE wechat_user ADD COLUMN openid VARCHAR(64) NULL AFTER unionid;
ALTER TABLE wechat_user ADD COLUMN ww_userid VARCHAR(64) NULL AFTER openid;
ALTER TABLE wechat_user ADD INDEX idx_openid (openid);
ALTER TABLE wechat_user ADD INDEX idx_ww_userid (ww_userid);
-- ----------------------------------------
-- V2.0.1 channel 列
-- ----------------------------------------
-- V2.0.1: cs-auth 加 channel 列（WECHAT_OA / WECHAT_WORK / WECHAT_MINI / LOCAL）
ALTER TABLE wechat_user ADD COLUMN channel VARCHAR(32) NULL AFTER role;
-- ============================================================
-- 2. cs-im 数据库
-- ============================================================
USE cs_im;


-- ----------------------------------------
-- V1.0.0 会话/消息/产品/订单基础表
-- ----------------------------------------
-- =====================================================
-- V1.0.0  cs-im schema（即时通信服务）
-- 数据库：cs_im
-- =====================================================

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL                COMMENT '业务会话ID',
    customer_id     VARCHAR(64)     NOT NULL                COMMENT '客户业务ID',
    agent_id        VARCHAR(64)     NULL                    COMMENT '客服业务ID',
    agent_name      VARCHAR(64)     NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'waiting' COMMENT 'waiting/active/ended/force_ended',
    channel         VARCHAR(16)     NOT NULL DEFAULT 'OA'    COMMENT 'OA/WORK/PHONE/SILENT',
    source          VARCHAR(32)     NULL                    COMMENT '来源（H5 / PC / SDK）',
    priority        TINYINT         NOT NULL DEFAULT 5,
    queue_pos       INT             NULL,
    start_time      DATETIME        NULL,
    end_time        DATETIME        NULL,
    end_reason      VARCHAR(128)    NULL,
    last_msg        TEXT            NULL,
    last_msg_time   DATETIME        NULL,
    msg_count       INT             NOT NULL DEFAULT 0,
    rating          TINYINT         NULL                    COMMENT '满意度 1-5',
    rating_comment  VARCHAR(256)    NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 消息表（按月分区由 DBA 维护；此处简单索引）
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    msg_id          VARCHAR(64)     NOT NULL                COMMENT '消息业务ID',
    session_id      VARCHAR(64)     NOT NULL,
    sender_id       VARCHAR(64)     NOT NULL,
    sender_name     VARCHAR(64)     NULL,
    sender_role     VARCHAR(16)     NOT NULL                COMMENT 'CUSTOMER/AGENT/ROBOT/SYSTEM',
    msg_type        VARCHAR(16)     NOT NULL DEFAULT 'text' COMMENT 'text/image/file/rich/system',
    content         TEXT            NULL,
    media_url       VARCHAR(512)    NULL,
    media_size      BIGINT          NULL,
    media_name      VARCHAR(128)    NULL,
    mention         VARCHAR(512)    NULL                    COMMENT '@人 列表',
    signature       VARCHAR(128)    NULL                    COMMENT 'HMAC-SHA256 签名',
    recalled        TINYINT(1)      NOT NULL DEFAULT 0,
    recall_time     DATETIME        NULL,
    reaction        VARCHAR(16)     NULL,
    client_ip       VARCHAR(64)     NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_msg_id (msg_id),
    INDEX idx_session_id (session_id),
    INDEX idx_sender_id (sender_id),
    INDEX idx_created_at (created_at),
    INDEX idx_session_time (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 文件上传记录
CREATE TABLE IF NOT EXISTS file_upload (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    file_id         VARCHAR(64)     NOT NULL                COMMENT '文件业务ID',
    uploader_id     VARCHAR(64)     NOT NULL,
    session_id      VARCHAR(64)     NULL,
    original_name   VARCHAR(256)    NOT NULL,
    storage_path    VARCHAR(512)    NOT NULL,
    storage_url     VARCHAR(512)    NULL,
    mime_type       VARCHAR(64)     NOT NULL,
    file_size       BIGINT          NOT NULL,
    file_hash       VARCHAR(128)    NULL                    COMMENT 'SHA-256',
    scan_status     VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/clean/dirty',
    scan_time       DATETIME        NULL,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_file_id (file_id),
    INDEX idx_uploader_id (uploader_id),
    INDEX idx_session_id (session_id),
    INDEX idx_scan_status (scan_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传记录';

-- 客服队列（实时排队）
CREATE TABLE IF NOT EXISTS agent_queue (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    priority        TINYINT         NOT NULL DEFAULT 5,
    enqueue_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assign_time     DATETIME        NULL,
    agent_id        VARCHAR(64)     NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'waiting' COMMENT 'waiting/assigned/timeout/cancelled',
    timeout_at      DATETIME        NULL                    COMMENT '排队超时时间',

    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_priority_time (priority DESC, enqueue_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服排队表';

-- 敏感词库
CREATE TABLE IF NOT EXISTS sensitive_word (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    word            VARCHAR(64)     NOT NULL,
    category        VARCHAR(32)     NOT NULL DEFAULT 'general' COMMENT 'general/political/porn/violence',
    level           TINYINT         NOT NULL DEFAULT 1       COMMENT '1=替换 2=拦截',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_word (word),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词库';

-- 初始敏感词
INSERT INTO sensitive_word (word, category, level) VALUES
    ('法轮功', 'political', 2),
    ('赌博', 'porn', 1),
    ('色情', 'porn', 2),
    ('暴力', 'violence', 1),
    ('诈骗', 'general', 2)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;-- =====================================================
-- V1.0.0  cs-trade schema（交易服务）
-- 数据库：cs_trade
-- =====================================================

-- 客户账户
CREATE TABLE IF NOT EXISTS customer_account (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    balance         DECIMAL(18, 2)  NOT NULL DEFAULT 0.00,
    frozen          DECIMAL(18, 2)  NOT NULL DEFAULT 0.00,
    total_recharge  DECIMAL(18, 2)  NOT NULL DEFAULT 0.00,
    total_consume   DECIMAL(18, 2)  NOT NULL DEFAULT 0.00,
    currency        VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    status          TINYINT(1)      NOT NULL DEFAULT 1,
    version         BIGINT          NOT NULL DEFAULT 0        COMMENT '乐观锁',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_customer_id (customer_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户账户';

-- 交易流水（不可变）
CREATE TABLE IF NOT EXISTS trade_record (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    trade_no        VARCHAR(64)     NOT NULL                COMMENT '业务流水号',
    customer_id     VARCHAR(64)     NOT NULL,
    type            VARCHAR(16)     NOT NULL                COMMENT 'recharge/consume/refund/transfer',
    amount          DECIMAL(18, 2)  NOT NULL,
    currency        VARCHAR(8)      NOT NULL DEFAULT 'CNY',
    balance_before  DECIMAL(18, 2)  NOT NULL,
    balance_after   DECIMAL(18, 2)  NOT NULL,
    channel         VARCHAR(16)     NULL                    COMMENT 'wechat/alipay/bank',
    channel_trade_no VARCHAR(64)    NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed/refunded',
    fail_reason     VARCHAR(256)    NULL,
    related_order   VARCHAR(64)     NULL,
    remark          VARCHAR(256)    NULL,
    operator_id     VARCHAR(64)     NULL,
    client_ip       VARCHAR(64)     NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_trade_no (trade_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易流水';

-- 产品表
CREATE TABLE IF NOT EXISTS product (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT            NULL,
    price           DECIMAL(18, 2)  NOT NULL,
    original_price  DECIMAL(18, 2)  NULL,
    category        VARCHAR(32)     NULL,
    stock           INT             NOT NULL DEFAULT 0,
    sales           INT             NOT NULL DEFAULT 0,
    image_url       VARCHAR(512)    NULL,
    enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_product_code (product_code),
    INDEX idx_enabled (enabled),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    product_code    VARCHAR(64)     NOT NULL,
    product_name    VARCHAR(128)    NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,
    unit_price      DECIMAL(18, 2)  NOT NULL,
    total_amount    DECIMAL(18, 2)  NOT NULL,
    discount_amount DECIMAL(18, 2)  NOT NULL DEFAULT 0.00,
    pay_amount      DECIMAL(18, 2)  NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/cancelled/refunded',
    pay_channel     VARCHAR(16)     NULL,
    pay_trade_no    VARCHAR(64)     NULL,
    paid_at         DATETIME        NULL,
    cancelled_at    DATETIME        NULL,
    remark          VARCHAR(256)    NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 演示数据
INSERT INTO product (product_code, name, description, price, original_price, category, stock) VALUES
    ('PRD-001', '标准版客服系统', '适合中小企业，3 坐席', 5800.00, 6800.00, 'service', 100),
    ('PRD-002', '企业版客服系统', '适合大型企业，10 坐席 + SLA', 18800.00, 22800.00, 'service', 50),
    ('PRD-003', '增值包：AI 智能客服', '意图识别 + 多轮对话', 9800.00, NULL, 'addon', 200)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;-- Trade module merged
-- ----------------------------------------
-- V2.0.0 金融产品 + 持仓 + 风险评估
-- ----------------------------------------
-- =====================================================
-- V2.0.0  cs-im 升级：金融产品 + 订单 + 风险评估 + 合规 + 持仓
-- 数据库：cs_im
-- =====================================================

-- 1) 产品表：扩展支持金融产品（在 V1.0.0 product 表基础上加列）
-- (Flyway 重复执行检查: baseline-on-migrate 已设为 true)
ALTER TABLE product ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT 'DEPOSIT' COMMENT 'INSURANCE/DEPOSIT/FUND/BOND';
ALTER TABLE product ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MID/HIGH';
ALTER TABLE product ADD COLUMN yield_rate DECIMAL(8, 4) NOT NULL DEFAULT 0.0 COMMENT '年化收益率 %';
ALTER TABLE product ADD COLUMN period VARCHAR(16) NOT NULL DEFAULT 'PERPETUAL' COMMENT '30D/90D/1Y/PERPETUAL';
ALTER TABLE product ADD COLUMN min_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN max_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.0 COMMENT '0=不限';
ALTER TABLE product ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/SUSPENDED/OFF_SHELF';
ALTER TABLE product ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;

-- 2) 客户账单
CREATE TABLE IF NOT EXISTS bill (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    biz_type        VARCHAR(32)     NOT NULL COMMENT 'PRODUCT_PURCHASE / REDEEM / FEE',
    amount          DECIMAL(18, 2)  NOT NULL,
    biz_date        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    related_order   VARCHAR(64)     NULL,
    remark          VARCHAR(256)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_biz_date (biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 金融订单
CREATE TABLE IF NOT EXISTS financial_order (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64)     NOT NULL,
    customer_id         VARCHAR(64)     NOT NULL,
    agent_username      VARCHAR(64)     NULL COMMENT '坐席协助下单（非空表示坐席代客下单）',
    product_code        VARCHAR(64)     NOT NULL,
    product_name        VARCHAR(128)    NOT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    payment_method      VARCHAR(32)     NOT NULL DEFAULT 'MOCK_BANK',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/RISK_ASSESSED/COMPLIANCE_PASSED/PAYING/SETTLED/REDEEMED/REJECTED/CANCELLED',
    risk_level          VARCHAR(16)     NULL COMMENT 'CONSERVATIVE/MODERATE/AGGRESSIVE',
    risk_score          INT             NULL,
    compliance_result   VARCHAR(16)     NULL COMMENT 'PASS/REJECTED',
    compliance_remark   VARCHAR(512)    NULL,
    paid_at             DATETIME        NULL,
    completed_at        DATETIME        NULL,
    redeemed_at         DATETIME        NULL,
    initiator_role      VARCHAR(32)     NULL COMMENT 'CUSTOMER/AGENT',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer (customer_id),
    INDEX idx_product (product_code),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='金融订单';

-- 4) 风险评估
CREATE TABLE IF NOT EXISTS risk_assessment (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    score           INT             NOT NULL COMMENT '0-100',
    risk_level      VARCHAR(16)     NOT NULL COMMENT 'CONSERVATIVE/MODERATE/AGGRESSIVE',
    answers_json    TEXT            NULL COMMENT '问卷答案 JSON',
    expires_at      DATETIME        NULL COMMENT '评估有效期',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险评估';

-- 5) 合规检查记录
CREATE TABLE IF NOT EXISTS compliance_check (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64)     NOT NULL,
    identity_check      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '实名认证',
    risk_check          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '风险评估',
    suitability_check   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '适当性匹配',
    aml_check           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '反洗钱',
    overall_result      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PASS/REJECTED',
    remark              VARCHAR(512)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规检查';

-- 6) 持仓
CREATE TABLE IF NOT EXISTS holding (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id         VARCHAR(64)     NOT NULL,
    product_code        VARCHAR(64)     NOT NULL,
    principal           DECIMAL(18, 2)  NOT NULL DEFAULT 0.0,
    accumulated_income  DECIMAL(18, 2)  NOT NULL DEFAULT 0.0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'HOLDING' COMMENT 'HOLDING/REDEEMED',
    start_date          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_date            DATETIME        NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持仓';

-- 7) 演示数据：5 个金融产品 (复用 V1.0.0 product 表的 product_code 列)
INSERT INTO product (product_code, name, description, price, original_price, category, stock, product_type, risk_level, yield_rate, period, min_amount, max_amount, status) VALUES
    ('PRD-INS-001', '平安出行险', '意外伤害保障 100 万', 50, NULL, 'service', 999, 'INSURANCE',  'LOW',    0.0,    '1Y',     50,     0,     'ON_SALE'),
    ('PRD-DEP-001', '稳赢 30 天', '30 天定期理财，稳健收益', 100, NULL, 'service', 999, 'DEPOSIT',    'LOW',    2.8,    '30D',    100,    50000, 'ON_SALE'),
    ('PRD-DEP-002', '稳赢 90 天', '90 天定期理财，收益更高', 1000, NULL, 'service', 999, 'DEPOSIT',    'MID',    3.5,    '90D',    1000,   100000,'ON_SALE'),
    ('PRD-FND-001', '成长基金',    '权益类基金，潜在高收益',  1000, NULL, 'service', 999, 'FUND',      'HIGH',   0.0,    'PERPETUAL', 1000, 0,     'ON_SALE'),
    ('PRD-BND-001', '国债 5 年',   '国家信用，5 年期',         1000, NULL, 'service', 999, 'BOND',      'LOW',    3.2,    '5Y',     1000,   200000,'ON_SALE')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;-- ----------------------------------------
-- V3.0.0 视频回放 + 工单 + FAQ
-- ----------------------------------------
-- =====================================================
-- V3.0.0  cs-im 升级：视频回溯 + 消息扩展字段
-- =====================================================

-- 1) chat_message 加 video_frame_url（视频回溯帧截图）
ALTER TABLE chat_message ADD COLUMN video_frame_url VARCHAR(512) NULL COMMENT '视频回溯帧截图URL';
ALTER TABLE chat_message ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0;

-- 2) chat_session 加 video_replay_url（完整会话回放视频）
ALTER TABLE chat_session ADD COLUMN video_replay_url VARCHAR(512) NULL COMMENT '完整会话视频回放地址';

-- 3) 工单系统（v1.9.0 E1）
CREATE TABLE IF NOT EXISTS ticket (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    ticket_no       VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    agent_username  VARCHAR(64)     NULL,
    session_id      BIGINT          NULL,
    title           VARCHAR(256)    NOT NULL,
    description     TEXT            NULL,
    category        VARCHAR(32)     NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL/COMPLAINT/CONSULT/BUG',
    priority        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/URGENT',
    status          VARCHAR(16)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ASSIGNED/PROCESSING/RESOLVED/CLOSED/CANCELLED',
    sla_deadline    DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at     DATETIME        NULL,
    closed_at       DATETIME        NULL,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_ticket_no (ticket_no),
    INDEX idx_customer (customer_id),
    INDEX idx_agent (agent_username),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服工单';

CREATE TABLE IF NOT EXISTS ticket_reply (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL,
    from_user       VARCHAR(64)     NOT NULL,
    from_role       VARCHAR(32)     NOT NULL COMMENT 'CUSTOMER/AGENT/SYSTEM',
    content         TEXT            NOT NULL,
    attachment_url  VARCHAR(512)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单回复';

-- 4) 知识库 FAQ（v1.9.0 E2）
CREATE TABLE IF NOT EXISTS faq_category (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(64)     NOT NULL,
    parent_id       BIGINT          NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    icon            VARCHAR(256)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 分类';

CREATE TABLE IF NOT EXISTS faq (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    category_id     BIGINT          NOT NULL,
    question        VARCHAR(512)    NOT NULL,
    answer          TEXT            NOT NULL,
    keywords        VARCHAR(512)    NULL COMMENT '逗号分隔的关键词',
    view_count      INT             NOT NULL DEFAULT 0,
    helpful_count   INT             NOT NULL DEFAULT 0,
    unhelpful_count INT             NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    FULLTEXT INDEX ftq_question (question, keywords),
    INDEX idx_category (category_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 问答';-- ----------------------------------------
-- V4.0.0 KYC 6 张表
-- ----------------------------------------
-- =====================================================
-- V4.0.0  cs-im 升级：完整 KYC（身份认证 + 视频双录 + 银行卡四要素）
-- =====================================================

-- 1) KYC 申请单
CREATE TABLE IF NOT EXISTS kyc_application (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_no      VARCHAR(64)     NOT NULL                                COMMENT '申请单号',
    customer_id         VARCHAR(64)     NOT NULL                                COMMENT '客户 ID',
    status              VARCHAR(32)     NOT NULL DEFAULT 'INIT'                  COMMENT 'INIT/OCR_UPLOADED/LIVENESS_PASSED/FACE_MATCHED/VIDEO_RECORDED/AUDITING/APPROVED/REJECTED/BANK_BINDING/COMPLETED',

    -- 身份证 OCR
    id_card_no          VARCHAR(32)     NULL                                    COMMENT '身份证号（加密存储）',
    id_card_name        VARCHAR(64)     NULL                                    COMMENT '身份证姓名',
    id_card_gender      VARCHAR(8)      NULL                                    COMMENT 'M/F',
    id_card_nation      VARCHAR(32)     NULL                                    COMMENT '民族',
    id_card_birth       VARCHAR(16)     NULL                                    COMMENT '生日 YYYY-MM-DD',
    id_card_address     VARCHAR(256)    NULL                                    COMMENT '地址',
    id_card_issue       VARCHAR(64)     NULL                                    COMMENT '签发机关',
    id_card_validity    VARCHAR(32)     NULL                                    COMMENT '有效期',
    id_card_front_img   VARCHAR(512)    NULL                                    COMMENT '身份证正面图',
    id_card_back_img    VARCHAR(512)    NULL                                    COMMENT '身份证反面图',
    ocr_raw_json        TEXT            NULL                                    COMMENT 'OCR 原始 JSON',

    -- 人脸比对
    face_img_url        VARCHAR(512)    NULL                                    COMMENT '活体照片 URL',
    face_match_score    DECIMAL(5, 2)  NULL                                    COMMENT '相似度 0-100',
    face_match_passed   TINYINT(1)      NULL                                    COMMENT '是否通过',

    -- 活体检测
    liveness_actions    VARCHAR(128)    NULL                                    COMMENT '活体动作序列',
    liveness_score      DECIMAL(5, 2)  NULL                                    COMMENT '活体分数',
    liveness_passed     TINYINT(1)      NULL                                    COMMENT '是否通过',

    -- 视频双录
    risk_statement_id   BIGINT          NULL                                    COMMENT '风险声明 ID',
    video_url           VARCHAR(512)    NULL                                    COMMENT '双录视频 URL',
    video_duration_sec  INT             NULL                                    COMMENT '视频时长（秒）',
    video_recorded_at   DATETIME        NULL                                    COMMENT '录制完成时间',

    -- 审核
    auditor_username    VARCHAR(64)     NULL                                    COMMENT '审核员',
    audit_score         DECIMAL(5, 2)  NULL                                    COMMENT '审核评分',
    audit_remark        VARCHAR(512)    NULL                                    COMMENT '审核备注',
    audited_at          DATETIME        NULL,

    -- 银行卡
    bank_card_no        VARCHAR(32)     NULL                                    COMMENT '卡号（加密）',
    bank_card_name      VARCHAR(64)     NULL                                    COMMENT '持卡人姓名',
    bank_card_mobile    VARCHAR(32)     NULL                                    COMMENT '预留手机号',
    bank_card_bind_at   DATETIME        NULL                                    COMMENT '绑卡时间',

    -- 风控
    risk_level          VARCHAR(16)     NULL                                    COMMENT 'LOW/MID/HIGH',
    blacklist_hit       TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否命中黑名单',

    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at        DATETIME        NULL                                    COMMENT 'KYC 完成时间',
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_application_no (application_no),
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_auditor (auditor_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 申请单';

-- 2) 风险声明库（监管要求的固定话术）
CREATE TABLE IF NOT EXISTS kyc_risk_statement (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64)     NOT NULL                                COMMENT '声明代码',
    title               VARCHAR(256)    NOT NULL                                COMMENT '声明标题',
    content             TEXT            NOT NULL                                COMMENT '声明全文（客户朗读）',
    category            VARCHAR(32)     NOT NULL DEFAULT 'GENERAL'              COMMENT 'GENERAL/INVESTMENT/INSURANCE',
    required_duration_sec INT           NOT NULL DEFAULT 10                    COMMENT '要求朗读时长',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED'            COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_code (code),
    INDEX idx_status (status),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险声明库';

-- 3) 双录视频记录（分段，每段一条）
CREATE TABLE IF NOT EXISTS kyc_video_record (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_id      BIGINT          NOT NULL                                COMMENT '申请单 ID',
    statement_id        BIGINT          NOT NULL                                COMMENT '声明 ID',
    segment_no          INT             NOT NULL DEFAULT 1                     COMMENT '段序号',
    video_url           VARCHAR(512)    NOT NULL                                COMMENT '视频 URL',
    duration_sec        INT             NOT NULL                                COMMENT '时长（秒）',
    file_size_kb        INT             NULL                                    COMMENT '文件大小（KB）',
    checksum            VARCHAR(64)     NULL                                    COMMENT 'SHA-256 校验',
    recorded_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 双录视频';

-- 4) 客户银行卡
CREATE TABLE IF NOT EXISTS bank_card (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id         VARCHAR(64)     NOT NULL,
    card_no_enc         VARCHAR(256)    NOT NULL                                COMMENT '卡号（加密）',
    card_no_masked      VARCHAR(32)     NOT NULL                                COMMENT '卡号掩码（如 **** **** **** 1234）',
    card_name           VARCHAR(64)     NOT NULL                                COMMENT '持卡人姓名',
    bank_code           VARCHAR(16)     NULL                                    COMMENT '银行代码（ICBC/CCB/ABC 等）',
    bank_name           VARCHAR(64)     NULL                                    COMMENT '银行名称',
    card_type           VARCHAR(16)     NOT NULL DEFAULT 'DEBIT'                COMMENT 'DEBIT/CREDIT',
    mobile              VARCHAR(32)     NULL                                    COMMENT '预留手机号',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否默认卡',
    verified            TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否四要素验证通过',
    verified_at         DATETIME        NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'BOUND'                COMMENT 'BOUND/UNBOUND/FROZEN',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_card_masked (card_no_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户绑卡';

-- 5) KYC 审核日志
CREATE TABLE IF NOT EXISTS kyc_audit_log (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_id      BIGINT          NOT NULL,
    auditor             VARCHAR(64)     NOT NULL,
    action              VARCHAR(32)     NOT NULL                                COMMENT 'SUBMIT/OCR/LIVENESS/FACE/VIDEO/AUDIT/APPROVE/REJECT/BIND',
    from_status         VARCHAR(32)     NULL,
    to_status           VARCHAR(32)     NULL,
    detail              VARCHAR(512)    NULL,
    ip                  VARCHAR(64)     NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id),
    INDEX idx_auditor (auditor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 审核日志';

-- 6) KYC 黑名单
CREATE TABLE IF NOT EXISTS kyc_blacklist (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    id_card_no_enc      VARCHAR(256)    NOT NULL                                COMMENT '身份证号（加密）',
    customer_id         VARCHAR(64)     NULL                                    COMMENT '关联客户',
    reason              VARCHAR(256)    NOT NULL                                COMMENT '加入原因',
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL'              COMMENT 'MANUAL/SYSTEM/POLICE',
    expires_at          DATETIME        NULL                                    COMMENT '过期时间（永久则 NULL）',
    created_by          VARCHAR(64)     NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_idcard (id_card_no_enc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 黑名单';

-- =====================================================
-- 演示数据：风险声明库（5 段监管要求的标准声明）
-- =====================================================
INSERT INTO kyc_risk_statement (code, title, content, category, required_duration_sec, sort_order) VALUES
('RS-INVEST-001', '投资者适当性声明', '本人确认：本人的风险承受能力等级与本次投资产品的风险等级相匹配。本人理解投资有风险，过往业绩不代表未来表现。本人具备相应的投资经验和资金实力，能够承担相应的投资风险。', 'INVESTMENT', 15, 1),
('RS-INVEST-002', '资金来源合法声明', '本人确认：本次投资的资金来源合法，不属于洗钱或恐怖融资。本人不是代表他人或单位进行投资。本人理解反洗钱法规的要求，并承诺配合相关尽职调查。', 'INVESTMENT', 12, 2),
('RS-INSURE-001', '投保意愿声明', '本人确认：本投保申请是基于本人真实意愿。本人已仔细阅读并理解保险条款、产品说明书和风险提示。本人自愿购买本保险产品。', 'INSURANCE', 10, 3),
('RS-GENERAL-001', '信息真实性声明', '本人确认：所提供的所有身份信息、联系方式和交易信息均真实、准确、完整、有效。如有变更将及时更新。', 'GENERAL', 8, 4),
('RS-FUND-001', '基金交易特别声明', '本人确认：已阅读基金合同、招募说明书，了解基金产品的风险等级、费率结构和赎回规则。本人理解基金投资可能面临本金损失风险。', 'INVESTMENT', 15, 5)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;-- ----------------------------------------
-- V5.0.0 AI 5 张表 + 8 条预置知识
-- ----------------------------------------
-- =====================================================
-- V5.0.0  cs-im 升级：坐席 AI 助手 + 多媒体（屏幕共享/语音/Markdown）
-- =====================================================

-- 1) AI 推荐话术
CREATE TABLE IF NOT EXISTS ai_suggestion (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL                                COMMENT '会话 ID',
    customer_id         VARCHAR(64)     NOT NULL                                COMMENT '客户 ID',
    agent_username      VARCHAR(64)     NOT NULL                                COMMENT '坐席',
    trigger_message_id  BIGINT          NULL                                    COMMENT '触发消息（客户消息）',
    suggestion_type     VARCHAR(32)     NOT NULL DEFAULT 'REPLY'               COMMENT 'REPLY/KNOWLEDGE/FAQ/ACTION',
    content             TEXT            NOT NULL                                COMMENT '推荐内容',
    confidence          DECIMAL(5, 2)   NULL                                    COMMENT '置信度 0-100',
    sources             VARCHAR(512)    NULL                                    COMMENT '引用来源 (faq_id/knowledge_id)',
    used                TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '坐席是否采纳',
    rating              TINYINT         NULL                                    COMMENT '坐席评分 1-5',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_agent (agent_username),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐话术';

-- 2) AI 知识库（向量化）
CREATE TABLE IF NOT EXISTS ai_knowledge (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    title               VARCHAR(256)    NOT NULL                                COMMENT '知识标题',
    content             TEXT            NOT NULL                                COMMENT '知识内容',
    category            VARCHAR(32)     NOT NULL DEFAULT 'GENERAL'              COMMENT 'GENERAL/PRODUCT/POLICY/FAQ',
    tags                VARCHAR(256)    NULL                                    COMMENT '逗号分隔标签',
    embedding           BLOB            NULL                                    COMMENT '向量化（768 维 float[]）',
    embedding_model     VARCHAR(64)     NULL                                    COMMENT 'embedding 模型',
    view_count          INT             NOT NULL DEFAULT 0,
    helpful_count       INT             NOT NULL DEFAULT 0,
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL'              COMMENT 'MANUAL/FAQ_SYNC/IMPORTED',
    source_id           BIGINT          NULL                                    COMMENT '关联源 ID',
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'              COMMENT 'ACTIVE/ARCHIVED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_category (category),
    INDEX idx_source (source, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库';

-- 3) 屏幕共享会话
CREATE TABLE IF NOT EXISTS screen_share_session (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL                                COMMENT '关联会话',
    initiator           VARCHAR(64)     NOT NULL                                COMMENT '发起方 (agent/customer)',
    peer                VARCHAR(64)     NOT NULL                                COMMENT '对方',
    status              VARCHAR(16)     NOT NULL DEFAULT 'INVITED'             COMMENT 'INVITED/ACTIVE/ENDED/REJECTED',
    sdp_offer           MEDIUMTEXT      NULL                                    COMMENT 'WebRTC offer',
    sdp_answer          MEDIUMTEXT      NULL                                    COMMENT 'WebRTC answer',
    started_at          DATETIME        NULL                                    COMMENT '开始时间',
    ended_at            DATETIME        NULL                                    COMMENT '结束时间',
    duration_sec        INT             NULL                                    COMMENT '总时长（秒）',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='屏幕共享会话';

-- 4) 语音消息
CREATE TABLE IF NOT EXISTS voice_message (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL,
    from_id             VARCHAR(64)     NOT NULL,
    from_role           VARCHAR(32)     NOT NULL,
    audio_url           VARCHAR(512)    NOT NULL                                COMMENT 'OSS 音频 URL',
    duration_sec        INT             NOT NULL                                COMMENT '时长（秒）',
    file_size_kb        INT             NULL                                    COMMENT '文件大小（KB）',
    waveform_data       TEXT            NULL                                    COMMENT '波形数据 JSON',
    transcription       VARCHAR(1024)   NULL                                    COMMENT 'ASR 转写文本',
    transcription_status VARCHAR(16)    NOT NULL DEFAULT 'PENDING'             COMMENT 'PENDING/SUCCESS/FAILED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_transcription (transcription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音消息';

-- 5) AI 推荐反馈（用于模型调优）
CREATE TABLE IF NOT EXISTS ai_feedback (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    suggestion_id       BIGINT          NOT NULL,
    agent_username      VARCHAR(64)     NOT NULL,
    feedback_type       VARCHAR(16)     NOT NULL                                COMMENT 'USED/SKIPPED/MODIFIED/RATED',
    rating              TINYINT         NULL                                    COMMENT '1-5 星',
    modified_content    TEXT            NULL                                    COMMENT '坐席修改后的内容',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐反馈';

-- =====================================================
-- 演示数据：5 条 AI 知识库（产品 + 政策 + FAQ）
-- =====================================================
INSERT INTO ai_knowledge (title, content, category, tags, view_count, helpful_count) VALUES
('理财产品起购金额', '稳赢 30 天起购 100 元；稳赢 90 天起购 1000 元；成长基金起购 1000 元；国债 5 年起购 1000 元；平安出行险 50 元起。', 'PRODUCT', '起购,理财,基金,保险', 0, 0),
('理财产品赎回规则', '活期类可随时赎回；定期类需持有到期；基金类 T+1 确认赎回。', 'PRODUCT', '赎回,理财,基金', 0, 0),
('风险评估有效期', '风险评估结果有效期为 1 年。过期后需重新评估才能购买新金融产品。', 'POLICY', '风险评估,KYC,合规', 0, 0),
('KYC 实名认证流程', 'KYC 认证包括 7 步：身份证 OCR、活体检测、人脸比对、视频双录、坐席审核、银行卡四要素。整个流程约 5 分钟。', 'POLICY', 'KYC,实名,认证', 0, 0),
('投诉处理时效', '普通投诉 24 小时内响应；紧急投诉 4 小时内响应。', 'POLICY', '投诉,SLA,工单', 0, 0);

INSERT INTO ai_knowledge (title, content, category, tags, view_count, helpful_count) VALUES
('账户余额查询', '客户问"我还有多少钱"/"余额"时，引导客户查看"我的"页面或调用 /auth/me 接口。', 'FAQ', '余额,账户,查询', 0, 0),
('转账失败原因', '常见原因：1) 余额不足；2) 收款方信息错误；3) 单笔超 5 万限额；4) 银行卡未 KYC 认证。', 'FAQ', '转账,失败,限额', 0, 0),
('密码忘记怎么办', '点击登录页"忘记密码"，通过预留手机号验证码重置。需要 KYC 已认证。', 'FAQ', '密码,忘记,重置', 0, 0);
-- ============================================================
-- 3. 验证
-- ============================================================
SELECT 'cs_auth' AS db, COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'cs_auth'
UNION ALL
SELECT 'cs_im', COUNT(*) FROM information_schema.tables WHERE table_schema = 'cs_im';

-- 预期 cs_auth: 4 张表（wechat_user, user_token, audit_log, blacklist）
-- 预期 cs_im:   27 张表
