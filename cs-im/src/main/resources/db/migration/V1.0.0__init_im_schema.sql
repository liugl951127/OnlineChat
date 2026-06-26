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
