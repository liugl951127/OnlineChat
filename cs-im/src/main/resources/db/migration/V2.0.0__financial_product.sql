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
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;