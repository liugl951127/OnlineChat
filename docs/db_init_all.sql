-- =====================================================
-- OnlineChat 一键初始化脚本（v2.2.0 重写版）
-- 数据库：MySQL 8.0.46+ / MariaDB 10.5+
-- 字符集：utf8mb4 / 排序：utf8mb4_unicode_ci / 引擎：InnoDB
-- 说明：从所有 @TableName domain 类生成，cs-auth 不依赖 Flyway
-- =====================================================
-- 用法：
--   mysql -uroot -p < db_init_all.sql
-- =====================================================

-- ============================================================
-- 0. 创建数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS cs_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cs_im   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 1. cs-auth（4 张表）
-- ============================================================
USE cs_auth;

-- WechatUser 用户主表
DROP TABLE IF EXISTS wechat_user;
CREATE TABLE wechat_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL                COMMENT '业务客户ID',
    openid          VARCHAR(64)     NULL                    COMMENT '微信公众号 openid',
    unionid         VARCHAR(64)     NULL                    COMMENT '微信 unionid',
    ww_userid       VARCHAR(64)     NULL                    COMMENT '企业微信 userid',
    username        VARCHAR(64)     NULL                    COMMENT '本地账号',
    provider        VARCHAR(20)     NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/WECHAT_OA/WECHAT_WORK/WECHAT_MINI/GITHUB/GOOGLE',
    provider_user_id VARCHAR(128)   NULL                    COMMENT 'OAuth 提供方用户ID',
    phone_enc       VARCHAR(256)    NULL                    COMMENT '手机号 AES 加密',
    phone_verified  TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '手机是否验证',
    password_hash   VARCHAR(256)    NULL                    COMMENT 'PBKDF2 哈希',
    nickname        VARCHAR(64)     NULL                    COMMENT '昵称',
    avatar          VARCHAR(512)    NULL                    COMMENT '头像URL',
    phone_masked    VARCHAR(20)     NULL                    COMMENT '脱敏手机号',
    login_fail_count INT            NOT NULL DEFAULT 0,
    lock_until      DATETIME        NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER' COMMENT 'CUSTOMER/AGENT/ADMIN',
    channel         VARCHAR(32)     NULL                    COMMENT 'WECHAT_OA/WECHAT_WORK/WECHAT_MINI/LOCAL',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '1=正常 0=禁用',
    last_login_time DATETIME        NULL,
    last_login_ip   VARCHAR(64)     NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_customer_id (customer_id),
    UNIQUE KEY uk_provider (provider, provider_user_id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone_enc (phone_enc),
    INDEX idx_openid (openid),
    INDEX idx_ww_userid (ww_userid),
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

-- UserToken 用户 Token 表
DROP TABLE IF EXISTS user_token;
CREATE TABLE user_token (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    token_id        VARCHAR(64)     NOT NULL                COMMENT 'JWT jti',
    issued_at       DATETIME        NOT NULL,
    expires_at      DATETIME        NOT NULL,
    revoked         TINYINT(1)      NOT NULL DEFAULT 0,
    revoke_reason   VARCHAR(64)     NULL,
    ip              VARCHAR(64)     NULL,
    user_agent      VARCHAR(256)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_token_id (token_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Token 表';

-- AuditLog 审计日志
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
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
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_operator_id (operator_id),
    INDEX idx_action (action),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';

-- Blacklist 黑名单（IP / 账号）
DROP TABLE IF EXISTS blacklist;
CREATE TABLE blacklist (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(16)     NOT NULL                COMMENT 'IP/USER/PHONE',
    value           VARCHAR(128)    NOT NULL,
    reason          VARCHAR(256)    NULL,
    expire_at       DATETIME        NULL                    COMMENT '过期时间，NULL=永久',
    create_by       VARCHAR(64)     NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_type_value (type, value),
    INDEX idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单';


-- ============================================================
-- 2. cs-im（23 张表）
-- ============================================================
USE cs_im;

-- ChatSession 会话
DROP TABLE IF EXISTS chat_session;
CREATE TABLE chat_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    agent_username  VARCHAR(64)     NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ROBOT'  COMMENT 'ROBOT/QUEUED/IN_SESSION/ENDED',
    robot_enabled   TINYINT(1)      NOT NULL DEFAULT 1,
    channel         VARCHAR(16)     NOT NULL DEFAULT 'WEB'    COMMENT 'WEB/MINI/OA/H5',
    last_message_at DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_agent (agent_username),
    INDEX idx_status (status),
    INDEX idx_last_message (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ChatMessage 消息
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT          NOT NULL,
    from_user       VARCHAR(64)     NOT NULL,
    from_role       VARCHAR(16)     NOT NULL DEFAULT 'CUSTOMER' COMMENT 'CUSTOMER/AGENT/ROBOT/SYSTEM',
    type            VARCHAR(16)     NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/FILE/VOICE/SYSTEM',
    content         TEXT            NULL,
    url             VARCHAR(512)    NULL,
    signature       VARCHAR(128)    NULL,
    recalled        TINYINT(1)      NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_from (from_user),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- UploadFile 文件上传
DROP TABLE IF EXISTS upload_file;
CREATE TABLE upload_file (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NULL,
    filename        VARCHAR(255)    NOT NULL,
    path            VARCHAR(512)    NOT NULL,
    url             VARCHAR(512)    NOT NULL,
    size_kb         INT             NOT NULL DEFAULT 0,
    mime_type       VARCHAR(64)     NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传记录';

-- AgentQueue 客服排队
DROP TABLE IF EXISTS agent_queue;
CREATE TABLE agent_queue (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT          NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    priority        INT             NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/ASSIGNED/CANCELED',
    agent_username  VARCHAR(64)     NULL,
    assigned_at     DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_status (status),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服排队表';

-- SensitiveWord 敏感词库
DROP TABLE IF EXISTS sensitive_word;
CREATE TABLE sensitive_word (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    word            VARCHAR(128)    NOT NULL,
    category        VARCHAR(32)     NULL,
    level           TINYINT         NOT NULL DEFAULT 1       COMMENT '1=替换 2=拒绝',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词库';

-- CustomerAccount 客户账户
DROP TABLE IF EXISTS customer_account;
CREATE TABLE customer_account (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    balance         DECIMAL(18,4)   NOT NULL DEFAULT 0,
    frozen          DECIMAL(18,4)   NOT NULL DEFAULT 0,
    risk_level      VARCHAR(16)     NOT NULL DEFAULT 'LOW'  COMMENT 'LOW/MID/HIGH',
    kyc_status      VARCHAR(16)     NOT NULL DEFAULT 'NOT_STARTED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户账户';

-- TradeRecord 交易流水
DROP TABLE IF EXISTS trade_record;
CREATE TABLE trade_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    type            VARCHAR(16)     NOT NULL                COMMENT 'BUY/SELL/REDEEM/DEPOSIT/WITHDRAW',
    amount          DECIMAL(18,4)   NOT NULL,
    balance_after   DECIMAL(18,4)   NOT NULL,
    related_order   VARCHAR(64)     NULL,
    remark          VARCHAR(256)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_type (type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易流水';

-- Product 金融产品
DROP TABLE IF EXISTS product;
CREATE TABLE product (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64)     NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    type            VARCHAR(32)     NOT NULL                COMMENT 'FUND/INSURANCE/BOND/FX',
    risk_level      VARCHAR(16)     NOT NULL DEFAULT 'LOW'  COMMENT 'LOW/MID/HIGH',
    min_amount      DECIMAL(18,4)   NOT NULL DEFAULT 0,
    annual_yield    DECIMAL(8,4)    NOT NULL DEFAULT 0,
    period_days     INT             NOT NULL DEFAULT 0,
    description     TEXT            NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_product_code (product_code),
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品表';

-- FinancialOrder 理财订单
DROP TABLE IF EXISTS financial_order;
CREATE TABLE financial_order (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    product_code    VARCHAR(64)     NOT NULL,
    amount          DECIMAL(18,4)   NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PAID/REDEEMED/CANCELED/FAILED',
    payment_method  VARCHAR(16)     NULL                    COMMENT 'BALANCE/BANK_CARD/THIRD',
    paid_at         DATETIME        NULL,
    redeemed_at     DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_customer (customer_id),
    INDEX idx_product (product_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- Holding 持仓
DROP TABLE IF EXISTS holding;
CREATE TABLE holding (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    product_code    VARCHAR(64)     NOT NULL,
    shares          DECIMAL(18,4)   NOT NULL DEFAULT 0,
    cost            DECIMAL(18,4)   NOT NULL DEFAULT 0,
    current_value   DECIMAL(18,4)   NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='持仓表';

-- RiskAssessment 风险评估
DROP TABLE IF EXISTS risk_assessment;
CREATE TABLE risk_assessment (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    score           INT             NOT NULL DEFAULT 0,
    level           VARCHAR(16)     NOT NULL DEFAULT 'LOW'  COMMENT 'LOW/MID/HIGH',
    answers_json    TEXT            NULL                    COMMENT '问卷答案 JSON',
    assessor        VARCHAR(64)     NULL                    COMMENT 'AGENT/SELF',
    expires_at      DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险评估';

-- ComplianceCheck 合规检查
DROP TABLE IF EXISTS compliance_check;
CREATE TABLE compliance_check (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    order_no        VARCHAR(64)     NULL,
    check_type      VARCHAR(32)     NOT NULL                COMMENT 'BUY/REDEEM/TRANSFER',
    passed          TINYINT(1)      NOT NULL DEFAULT 0,
    failed_rules    VARCHAR(512)    NULL                    COMMENT '失败的检查项',
    detail          TEXT            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合规检查';

-- Ticket 工单
DROP TABLE IF EXISTS ticket;
CREATE TABLE ticket (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_no       VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    content         TEXT            NOT NULL,
    type            VARCHAR(32)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT/HIGH/LOW',
    status          VARCHAR(16)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ASSIGNED/PROCESSING/RESOLVED/CLOSED/CANCELED',
    priority        TINYINT         NOT NULL DEFAULT 3      COMMENT '1-5，1 最高',
    assignee        VARCHAR(64)     NULL,
    sla_hours       INT             NOT NULL DEFAULT 24,
    resolved_at     DATETIME        NULL,
    closed_at       DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_ticket_no (ticket_no),
    INDEX idx_customer (customer_id),
    INDEX idx_assignee (assignee),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单';

-- TicketReply 工单回复
DROP TABLE IF EXISTS ticket_reply;
CREATE TABLE ticket_reply (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_no       VARCHAR(64)     NOT NULL,
    replier         VARCHAR(64)     NOT NULL,
    replier_role    VARCHAR(16)     NOT NULL                COMMENT 'CUSTOMER/AGENT/SYSTEM',
    content         TEXT            NOT NULL,
    internal        TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '内部备注（客户不可见）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_ticket (ticket_no),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单回复';

-- FaqCategory FAQ 分类
DROP TABLE IF EXISTS faq_category;
CREATE TABLE faq_category (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(64)     NOT NULL,
    parent_id       BIGINT          NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    icon            VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 分类';

-- Faq FAQ 知识
DROP TABLE IF EXISTS faq;
CREATE TABLE faq (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id     BIGINT          NULL,
    title           VARCHAR(255)    NOT NULL,
    content         TEXT            NOT NULL,
    tags            VARCHAR(255)    NULL,
    view_count      INT             NOT NULL DEFAULT 0,
    helpful_count   INT             NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_category (category_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ';

-- Bill 账单
DROP TABLE IF EXISTS bill;
CREATE TABLE bill (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    type            VARCHAR(16)     NOT NULL                COMMENT 'INCOME/EXPENSE',
    amount          DECIMAL(18,4)   NOT NULL,
    category        VARCHAR(32)     NULL,
    description     VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单';

-- KycApplication KYC 申请单
DROP TABLE IF EXISTS kyc_application;
CREATE TABLE kyc_application (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_no  VARCHAR(64)     NOT NULL,
    customer_id     VARCHAR(64)     NOT NULL,
    real_name       VARCHAR(64)     NULL,
    gender          VARCHAR(2)      NULL,
    nation          VARCHAR(16)     NULL,
    birth_date      DATE            NULL,
    id_card_no      VARCHAR(64)     NULL,
    id_card_address VARCHAR(255)    NULL,
    issue_authority VARCHAR(128)    NULL,
    valid_period    VARCHAR(64)     NULL,
    mobile          VARCHAR(20)     NULL,
    bank_card_no    VARCHAR(64)     NULL,
    bank_name       VARCHAR(64)     NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'INIT'  COMMENT 'INIT/OCR_UPLOADED/LIVENESS_PASSED/FACE_MATCHED/VIDEO_RECORDED/AUDITING/APPROVED/REJECTED/BANK_BINDING/COMPLETED',
    risk_level      VARCHAR(16)     NULL                    COMMENT 'LOW/MID/HIGH',
    blacklist_hit   TINYINT(1)      NOT NULL DEFAULT 0,
    ocr_raw_json    TEXT            NULL                    COMMENT 'OCR 厂商原始返回',
    liveness_score  DECIMAL(5,2)    NULL,
    face_match_score DECIMAL(5,2)   NULL,
    auditor         VARCHAR(64)     NULL,
    audit_remark    VARCHAR(512)    NULL,
    audited_at      DATETIME        NULL,
    submitted_at    DATETIME        NULL,
    completed_at    DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_application_no (application_no),
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_auditor (auditor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 申请单';

-- KycRiskStatement 风险声明
DROP TABLE IF EXISTS kyc_risk_statement;
CREATE TABLE kyc_risk_statement (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)     NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    content         TEXT            NOT NULL,
    duration_sec    INT             NOT NULL DEFAULT 15,
    required        TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 风险声明';

-- KycVideoRecord 双录视频
DROP TABLE IF EXISTS kyc_video_record;
CREATE TABLE kyc_video_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT          NOT NULL,
    statement_id    BIGINT          NULL,
    segment_no      INT             NOT NULL DEFAULT 1,
    video_url       VARCHAR(512)    NULL,
    duration_sec    INT             NOT NULL DEFAULT 0,
    file_size_kb    INT             NULL,
    checksum        VARCHAR(128)    NULL,
    recorded_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id),
    INDEX idx_statement (statement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 双录视频';

-- BankCard 绑卡
DROP TABLE IF EXISTS bank_card;
CREATE TABLE bank_card (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     VARCHAR(64)     NOT NULL,
    card_no_enc     VARCHAR(256)    NULL                    COMMENT 'AES 加密',
    card_no_masked  VARCHAR(32)     NOT NULL                COMMENT '脱敏卡号',
    card_name       VARCHAR(64)     NOT NULL                COMMENT '持卡人',
    bank_code       VARCHAR(32)     NULL,
    bank_name       VARCHAR(64)     NULL,
    card_type       VARCHAR(16)     NOT NULL DEFAULT 'DEBIT' COMMENT 'DEBIT/CREDIT',
    mobile          VARCHAR(20)     NULL,
    verified        TINYINT(1)      NOT NULL DEFAULT 0,
    verified_at     DATETIME        NULL,
    primary_card    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='银行卡';

-- KycAuditLog KYC 审核日志
DROP TABLE IF EXISTS kyc_audit_log;
CREATE TABLE kyc_audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT          NOT NULL,
    auditor         VARCHAR(64)     NOT NULL,
    action          VARCHAR(32)     NOT NULL                COMMENT 'SUBMIT/APPROVE/REJECT',
    from_status     VARCHAR(32)     NULL,
    to_status       VARCHAR(32)     NULL,
    remark          VARCHAR(512)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 审核日志';

-- KycBlacklist KYC 黑名单
DROP TABLE IF EXISTS kyc_blacklist;
CREATE TABLE kyc_blacklist (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(16)     NOT NULL                COMMENT 'IDCARD/MOBILE/DEVICE',
    value           VARCHAR(128)    NOT NULL,
    reason          VARCHAR(255)    NULL,
    source          VARCHAR(32)     NULL                    COMMENT 'MANUAL/AUTO',
    expire_at       DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_type_value (type, value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 黑名单';

-- AiSuggestion AI 推荐话术
DROP TABLE IF EXISTS ai_suggestion;
CREATE TABLE ai_suggestion (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT      NOT NULL,
    customer_id         VARCHAR(64) NOT NULL,
    agent_username      VARCHAR(64) NOT NULL,
    trigger_message_id  BIGINT      NULL,
    suggestion_type     VARCHAR(32) NOT NULL DEFAULT 'REPLY' COMMENT 'REPLY/KNOWLEDGE/FAQ/ACTION',
    content             TEXT        NOT NULL,
    confidence          DECIMAL(5,2) NULL,
    sources             VARCHAR(512) NULL,
    used                TINYINT(1)  NOT NULL DEFAULT 0,
    rating              TINYINT     NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)  NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_agent (agent_username),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐话术';

-- AiKnowledge AI 知识库
DROP TABLE IF EXISTS ai_knowledge;
CREATE TABLE ai_knowledge (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title               VARCHAR(256)    NOT NULL,
    content             TEXT            NOT NULL,
    category            VARCHAR(32)     NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL/PRODUCT/POLICY/FAQ',
    tags                VARCHAR(256)    NULL,
    embedding           BLOB            NULL                    COMMENT '向量化（768 维 float[]）',
    embedding_model     VARCHAR(64)     NULL,
    view_count          INT             NOT NULL DEFAULT 0,
    helpful_count       INT             NOT NULL DEFAULT 0,
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/FAQ_SYNC/IMPORTED',
    source_id           BIGINT          NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_category (category),
    INDEX idx_source (source, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库';

-- AiFeedback AI 反馈
DROP TABLE IF EXISTS ai_feedback;
CREATE TABLE ai_feedback (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    suggestion_id       BIGINT      NOT NULL,
    agent_username      VARCHAR(64) NOT NULL,
    feedback_type       VARCHAR(16) NOT NULL                    COMMENT 'USED/SKIPPED/MODIFIED/RATED',
    rating              TINYINT     NULL,
    modified_content    TEXT        NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)  NOT NULL DEFAULT 0,

    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐反馈';

-- ScreenShareSession 屏幕共享
DROP TABLE IF EXISTS screen_share_session;
CREATE TABLE screen_share_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL,
    initiator           VARCHAR(64)     NOT NULL                COMMENT 'agent/customer',
    peer                VARCHAR(64)     NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'INVITED' COMMENT 'INVITED/ACTIVE/ENDED/REJECTED',
    sdp_offer           MEDIUMTEXT      NULL,
    sdp_answer          MEDIUMTEXT      NULL,
    started_at          DATETIME        NULL,
    ended_at            DATETIME        NULL,
    duration_sec        INT             NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='屏幕共享会话';

-- VoiceMessage 语音消息
DROP TABLE IF EXISTS voice_message;
CREATE TABLE voice_message (
    id                      BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id              BIGINT          NOT NULL,
    from_id                 VARCHAR(64)     NOT NULL,
    from_role               VARCHAR(32)     NOT NULL,
    audio_url               VARCHAR(512)    NOT NULL,
    duration_sec            INT             NOT NULL,
    file_size_kb            INT             NULL,
    waveform_data           TEXT            NULL,
    transcription           VARCHAR(1024)   NULL,
    transcription_status    VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_transcription (transcription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音消息';


-- ============================================================
-- 3. 演示数据
-- ============================================================
USE cs_im;

-- 金融产品演示数据
INSERT INTO product (product_code, name, type, risk_level, min_amount, annual_yield, period_days, description) VALUES
('FUND-001', '稳赢 30 天', 'FUND',     'LOW',  100.0000,  3.2000,  30,   '30 天短期理财，年化 3.2%'),
('FUND-002', '稳赢 90 天', 'FUND',     'LOW',  1000.0000, 3.8000,  90,   '90 天中期理财，年化 3.8%'),
('FUND-003', '成长基金',   'FUND',     'MID',  1000.0000, 6.5000, 1825, '5 年期成长型基金，年化 6.5%'),
('BOND-001', '国债 5 年',  'BOND',     'LOW',  1000.0000, 3.5000, 1825, '5 年期记账式国债'),
('INS-001',  '平安出行险', 'INSURANCE', 'LOW',  50.0000,   0.0000, 365,  '出行意外险，年缴 50 元');

-- AI 知识库演示数据
INSERT INTO ai_knowledge (title, content, category, tags) VALUES
('理财产品起购金额', '稳赢 30 天起购 100 元；稳赢 90 天起购 1000 元；成长基金起购 1000 元；国债 5 年起购 1000 元；平安出行险 50 元起。', 'PRODUCT', '起购,理财,基金,保险'),
('理财产品赎回规则', '活期类可随时赎回；定期类需持有到期；基金类 T+1 确认赎回。', 'PRODUCT', '赎回,理财,基金'),
('风险评估有效期', '风险评估结果有效期为 1 年。过期后需重新评估才能购买新金融产品。', 'POLICY', '风险评估,KYC,合规'),
('KYC 实名认证流程', 'KYC 认证包括 7 步：身份证 OCR、活体检测、人脸比对、视频双录、坐席审核、银行卡四要素。整个流程约 5 分钟。', 'POLICY', 'KYC,实名,认证'),
('投诉处理时效', '普通投诉 24 小时内响应；紧急投诉 4 小时内响应。', 'POLICY', '投诉,SLA,工单'),
('账户余额查询', '客户问"余额"时，引导客户查看"我的"页面或调用 /auth/me 接口。', 'FAQ', '余额,账户,查询'),
('转账失败原因', '常见原因：1) 余额不足；2) 收款方信息错误；3) 单笔超 5 万限额；4) 银行卡未 KYC 认证。', 'FAQ', '转账,失败,限额'),
('密码忘记怎么办', '点击登录页"忘记密码"，通过预留手机号验证码重置。需要 KYC 已认证。', 'FAQ', '密码,忘记,重置');

-- KYC 风险声明预置
INSERT INTO kyc_risk_statement (code, title, content, duration_sec, sort_order) VALUES
('RS-INVEST-001', '投资者适当性声明', '本人确认所提供的信息真实有效，自主作出投资决策，自行承担投资风险。', 15, 1),
('RS-INVEST-002', '资金来源合法声明', '本人用于投资的资金来源合法合规，不涉及洗钱、恐怖融资或其他违法活动。', 12, 2),
('RS-INSURE-001', '投保意愿声明', '本人基于真实意愿投保，已阅读并理解保险条款及免责事项。', 10, 3),
('RS-GENERAL-001', '信息真实性声明', '本人提供的所有身份信息、银行账户信息均真实、准确、完整。', 8, 4),
('RS-FUND-001', '基金交易特别声明', '本人已了解基金投资风险，知晓基金非保本产品，可能存在本金损失。', 15, 5);

-- FAQ 分类演示
INSERT INTO faq_category (id, name, parent_id, sort_order) VALUES
(1, '账户问题', NULL, 1),
(2, '理财产品', NULL, 2),
(3, 'KYC 认证', NULL, 3),
(4, '密码相关', 1, 1),
(5, '余额查询', 1, 2);

-- 敏感词演示
INSERT INTO sensitive_word (word, category, level) VALUES
('诈骗', 'ILLEGAL', 2),
('洗钱', 'ILLEGAL', 2),
('法轮功', 'POLITICS', 2);

-- 默认管理员（密码 admin，运行时由 init 任务覆盖）
INSERT INTO wechat_user (customer_id, username, nickname, role, provider, status) VALUES
('admin-001', 'admin', '系统管理员', 'ADMIN', 'LOCAL', 1);


-- ============================================================
-- 4. 验证
-- ============================================================
SELECT 'cs_auth' AS db, COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'cs_auth'
UNION ALL
SELECT 'cs_im', COUNT(*) FROM information_schema.tables WHERE table_schema = 'cs_im';
-- 预期：cs_auth=4 张表，cs_im=23 张表，总计 27 张