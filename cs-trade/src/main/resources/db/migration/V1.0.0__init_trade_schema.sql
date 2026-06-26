-- =====================================================
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
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;