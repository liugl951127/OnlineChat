-- =====================================================
-- OnlineChat 一键初始化脚本（v2.2.3 自动生成）
-- 数据库：MySQL 8.0.46+ / MariaDB 10.5+
-- 生成时间：自动
-- 字符集：utf8mb4 / 排序：utf8mb4_unicode_ci / 引擎：InnoDB
-- 说明：从所有 @TableName domain 类自动生成
-- =====================================================
-- 用法：
--   mysql -uroot -p < db_init_all.sql
-- =====================================================

-- ============================================================
-- 0. 创建数据库
-- ============================================================
CREATE DATABASE IF NOT EXISTS cs_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cs_im CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS cs_message CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 1. cs-auth（1 张表）
-- ============================================================
USE cs_auth;

-- WechatUser（自动生成）
DROP TABLE IF EXISTS wechat_user;
CREATE TABLE wechat_user (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    openid                VARCHAR(64) NULL,
    unionid               VARCHAR(64) NULL,
    ww_userid             VARCHAR(64) NULL,
    username              VARCHAR(64) NULL,
    provider              VARCHAR(64) NULL,
    provider_user_id      VARCHAR(64) NULL,
    phone_enc             VARCHAR(255) NULL,
    phone_verified        INT NULL,
    password_hash         VARCHAR(255) NULL,
    nickname              VARCHAR(64) NULL,
    avatar                VARCHAR(255) NULL,
    phone_masked          VARCHAR(255) NULL,
    login_fail_count      INT NULL,
    lock_until            VARCHAR(255) NULL,
    role                  VARCHAR(64) NULL,
    status                INT NULL,
    channel               VARCHAR(64) NULL,
    last_login_time       DATETIME NULL,
    last_login_ip         VARCHAR(255) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='WechatUser';

-- ============================================================
-- 2. cs-im（22 张表）
-- ============================================================
USE cs_im;

-- AiFeedback（自动生成）
DROP TABLE IF EXISTS ai_feedback;
CREATE TABLE ai_feedback (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    suggestion_id         BIGINT NULL,
    agent_username        VARCHAR(64) NULL,
    feedback_type         VARCHAR(64) NULL,
    rating                INT NULL,
    modified_content      TEXT NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_agent (agent_username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AiFeedback';

-- AiKnowledge（自动生成）
DROP TABLE IF EXISTS ai_knowledge;
CREATE TABLE ai_knowledge (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title                 VARCHAR(255) NULL,
    content               TEXT NULL,
    category              VARCHAR(64) NULL,
    tags                  VARCHAR(255) NULL,
    embedding             BLOB NULL,
    embedding_model       VARCHAR(255) NULL,
    view_count            INT NULL,
    helpful_count         INT NULL,
    source                VARCHAR(255) NULL,
    source_id             BIGINT NULL,
    status                VARCHAR(64) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AiKnowledge';

-- AiSuggestion（自动生成）
DROP TABLE IF EXISTS ai_suggestion;
CREATE TABLE ai_suggestion (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT NULL,
    customer_id           VARCHAR(64) NULL,
    agent_username        VARCHAR(64) NULL,
    trigger_message_id    BIGINT NULL,
    suggestion_type       VARCHAR(64) NULL,
    content               TEXT NULL,
    confidence            DECIMAL(18,4) NULL,
    sources               VARCHAR(255) NULL,
    used                  TINYINT(1) NULL,
    rating                INT NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_session (session_id),
    INDEX idx_agent (agent_username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AiSuggestion';

-- BankCard（自动生成）
DROP TABLE IF EXISTS bank_card;
CREATE TABLE bank_card (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    card_no_enc           VARCHAR(64) NULL,
    card_no_masked        VARCHAR(64) NULL,
    card_name             VARCHAR(64) NULL,
    bank_code             VARCHAR(64) NULL,
    bank_name             VARCHAR(64) NULL,
    card_type             VARCHAR(64) NULL,
    mobile                VARCHAR(255) NULL,
    is_default            INT NULL,
    verified              INT NULL,
    verified_at           DATETIME NULL,
    status                VARCHAR(64) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BankCard';

-- Bill（自动生成）
DROP TABLE IF EXISTS bill;
CREATE TABLE bill (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    biz_type              VARCHAR(64) NULL,
    amount                DECIMAL(18,4) NULL,
    biz_date              DATE NULL,
    status                VARCHAR(64) NULL,
    remark                TEXT NULL,
    created_at            DATETIME NULL,
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bill';

-- ChatMessage（自动生成）
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT NULL,
    from_user             VARCHAR(255) NULL,
    from_role             VARCHAR(64) NULL,
    type                  VARCHAR(64) NULL,
    content               TEXT NULL,
    signature             VARCHAR(255) NULL,
    reply_to_id           BIGINT NULL,
    recalled              INT NULL,
    recalled_at           DATETIME NULL,
    recalled_by           VARCHAR(255) NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    video_frame_url       VARCHAR(64) NULL,
    INDEX idx_session (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ChatMessage';

-- ChatSession（自动生成）
DROP TABLE IF EXISTS chat_session;
CREATE TABLE chat_session (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    agent_username        VARCHAR(64) NULL,
    status                VARCHAR(255) NULL,
    queued_at             DATETIME NULL,
    accepted_at           DATETIME NULL,
    last_active_at        DATETIME NULL,
    last_message_at       DATETIME NULL,
    ended_at              DATETIME NULL,
    ended_by              VARCHAR(255) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    video_replay_url      VARCHAR(64) NULL,
    INDEX idx_customer (customer_id),
    INDEX idx_agent (agent_username),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ChatSession';

-- ComplianceCheck（自动生成）
DROP TABLE IF EXISTS compliance_check;
CREATE TABLE compliance_check (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no              VARCHAR(64) NULL,
    identity_check        INT NULL,
    risk_check            INT NULL,
    suitability_check     INT NULL,
    aml_check             INT NULL,
    overall_result        VARCHAR(255) NULL,
    remark                TEXT NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ComplianceCheck';

-- Faq（自动生成）
DROP TABLE IF EXISTS faq;
CREATE TABLE faq (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id           BIGINT NULL,
    question              VARCHAR(255) NULL,
    answer                VARCHAR(255) NULL,
    keywords              VARCHAR(255) NULL,
    view_count            INT NULL,
    helpful_count         INT NULL,
    unhelpful_count       INT NULL,
    status                VARCHAR(64) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Faq';

-- FaqCategory（自动生成）
DROP TABLE IF EXISTS faq_category;
CREATE TABLE faq_category (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(64) NULL,
    parent_id             BIGINT NULL,
    sort_order            INT NULL,
    icon                  VARCHAR(255) NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FaqCategory';

-- FinancialOrder（自动生成）
DROP TABLE IF EXISTS financial_order;
CREATE TABLE financial_order (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no              VARCHAR(64) NULL,
    customer_id           VARCHAR(64) NULL,
    agent_username        VARCHAR(64) NULL,
    product_code          VARCHAR(64) NULL,
    product_name          VARCHAR(64) NULL,
    amount                DECIMAL(18,4) NULL,
    payment_method        VARCHAR(255) NULL,
    status                VARCHAR(64) NULL,
    risk_level            VARCHAR(64) NULL,
    risk_score            INT NULL,
    compliance_result     VARCHAR(255) NULL,
    compliance_remark     TEXT NULL,
    initiator_role        VARCHAR(64) NULL,
    paid_at               DATETIME NULL,
    completed_at          DATETIME NULL,
    redeemed_at           DATETIME NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_agent (agent_username),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FinancialOrder';

-- Holding（自动生成）
DROP TABLE IF EXISTS holding;
CREATE TABLE holding (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    product_code          VARCHAR(64) NULL,
    principal             DECIMAL(18,4) NULL,
    accumulated_income    DECIMAL(18,4) NULL,
    status                VARCHAR(64) NULL,
    start_date            DATETIME NULL,
    end_date              DATETIME NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Holding';

-- KycApplication（自动生成）
DROP TABLE IF EXISTS kyc_application;
CREATE TABLE kyc_application (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_no        VARCHAR(64) NULL,
    customer_id           VARCHAR(64) NULL,
    status                VARCHAR(64) NULL,
    id_card_no            VARCHAR(64) NULL,
    id_card_name          VARCHAR(64) NULL,
    id_card_gender        VARCHAR(64) NULL,
    id_card_nation        VARCHAR(64) NULL,
    id_card_birth         VARCHAR(64) NULL,
    id_card_address       VARCHAR(64) NULL,
    id_card_issue         VARCHAR(64) NULL,
    id_card_validity      VARCHAR(64) NULL,
    id_card_front_img     VARCHAR(64) NULL,
    id_card_back_img      VARCHAR(64) NULL,
    ocr_raw_json          TEXT NULL,
    face_img_url          VARCHAR(512) NULL,
    face_match_score      DECIMAL(18,4) NULL,
    face_match_passed     INT NULL,
    liveness_actions      VARCHAR(255) NULL,
    liveness_score        DECIMAL(18,4) NULL,
    liveness_passed       INT NULL,
    risk_statement_id     BIGINT NULL,
    video_url             VARCHAR(64) NULL,
    video_duration_sec    INT NULL,
    video_recorded_at     DATETIME NULL,
    auditor_username      VARCHAR(64) NULL,
    audit_score           DECIMAL(18,4) NULL,
    audit_remark          TEXT NULL,
    audited_at            DATETIME NULL,
    bank_card_no          VARCHAR(64) NULL,
    bank_card_name        VARCHAR(64) NULL,
    bank_card_mobile      VARCHAR(255) NULL,
    bank_card_bind_at     DATETIME NULL,
    risk_level            VARCHAR(64) NULL,
    blacklist_hit         INT NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    completed_at          DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KycApplication';

-- KycRiskStatement（自动生成）
DROP TABLE IF EXISTS kyc_risk_statement;
CREATE TABLE kyc_risk_statement (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                  VARCHAR(64) NULL,
    title                 VARCHAR(255) NULL,
    content               TEXT NULL,
    category              VARCHAR(64) NULL,
    required_duration_sec INT NULL,
    status                VARCHAR(64) NULL,
    sort_order            INT NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KycRiskStatement';

-- KycVideoRecord（自动生成）
DROP TABLE IF EXISTS kyc_video_record;
CREATE TABLE kyc_video_record (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id        BIGINT NULL,
    statement_id          BIGINT NULL,
    segment_no            INT NULL,
    video_url             VARCHAR(64) NULL,
    duration_sec          INT NULL,
    file_size_kb          INT NULL,
    checksum              VARCHAR(255) NULL,
    recorded_at           DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KycVideoRecord';

-- Product（自动生成）
DROP TABLE IF EXISTS product;
CREATE TABLE product (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_code          VARCHAR(64) NULL,
    name                  VARCHAR(64) NULL,
    description           VARCHAR(255) NULL,
    product_type          VARCHAR(64) NULL,
    risk_level            VARCHAR(64) NULL,
    yield_rate            DECIMAL(18,4) NULL,
    period                VARCHAR(255) NULL,
    min_amount            DECIMAL(18,4) NULL,
    max_amount            DECIMAL(18,4) NULL,
    status                VARCHAR(64) NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Product';

-- RiskAssessment（自动生成）
DROP TABLE IF EXISTS risk_assessment;
CREATE TABLE risk_assessment (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id           VARCHAR(64) NULL,
    score                 INT NULL,
    risk_level            VARCHAR(64) NULL,
    answers_json          TEXT NULL,
    expires_at            DATETIME NULL,
    created_at            DATETIME NULL,
    INDEX idx_customer (customer_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RiskAssessment';

-- ScreenShareSession（自动生成）
DROP TABLE IF EXISTS screen_share_session;
CREATE TABLE screen_share_session (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT NULL,
    initiator             VARCHAR(255) NULL,
    peer                  VARCHAR(255) NULL,
    status                VARCHAR(64) NULL,
    sdp_offer             VARCHAR(255) NULL,
    sdp_answer            VARCHAR(255) NULL,
    started_at            DATETIME NULL,
    ended_at              DATETIME NULL,
    duration_sec          INT NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_session (session_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ScreenShareSession';

-- Ticket（自动生成）
DROP TABLE IF EXISTS ticket;
CREATE TABLE ticket (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_no             VARCHAR(64) NULL,
    customer_id           VARCHAR(64) NULL,
    agent_username        VARCHAR(64) NULL,
    session_id            BIGINT NULL,
    title                 VARCHAR(255) NULL,
    description           VARCHAR(255) NULL,
    category              VARCHAR(64) NULL,
    priority              VARCHAR(255) NULL,
    status                VARCHAR(64) NULL,
    sla_deadline          DATETIME NULL,
    created_at            DATETIME NULL,
    updated_at            DATETIME NULL,
    resolved_at           DATETIME NULL,
    closed_at             DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_customer (customer_id),
    INDEX idx_session (session_id),
    INDEX idx_agent (agent_username),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Ticket';

-- TicketReply（自动生成）
DROP TABLE IF EXISTS ticket_reply;
CREATE TABLE ticket_reply (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id             BIGINT NULL,
    from_user             VARCHAR(255) NULL,
    from_role             VARCHAR(64) NULL,
    content               TEXT NULL,
    attachment_url        VARCHAR(512) NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TicketReply';

-- UploadFile（自动生成）
DROP TABLE IF EXISTS upload_file;
CREATE TABLE upload_file (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    file_id               VARCHAR(64) NULL,
    uploader_id           VARCHAR(64) NULL,
    session_id            VARCHAR(64) NULL,
    original_name         VARCHAR(64) NULL,
    storage_path          VARCHAR(255) NULL,
    storage_url           VARCHAR(512) NULL,
    mime_type             VARCHAR(64) NULL,
    file_size             BIGINT NULL,
    file_hash             VARCHAR(255) NULL,
    scan_status           VARCHAR(64) NULL,
    scan_time             DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    created_at            DATETIME NULL,
    INDEX idx_session (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UploadFile';

-- VoiceMessage（自动生成）
DROP TABLE IF EXISTS voice_message;
CREATE TABLE voice_message (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id            BIGINT NULL,
    from_id               VARCHAR(64) NULL,
    from_role             VARCHAR(64) NULL,
    audio_url             VARCHAR(512) NULL,
    duration_sec          INT NULL,
    file_size_kb          INT NULL,
    waveform_data         VARCHAR(255) NULL,
    transcription         VARCHAR(255) NULL,
    transcription_status  VARCHAR(64) NULL,
    created_at            DATETIME NULL,
    deleted               TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_session (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VoiceMessage';

-- ============================================================
-- 2.5 cs-message（消息持久化）
-- ============================================================
USE cs_message;

-- OfflineMessage 离线消息表（持久化备份，Redis 主存）
DROP TABLE IF EXISTS offline_message;
CREATE TABLE offline_message (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL                    COMMENT '接收人 customerId',
    msg_id          VARCHAR(64)     NOT NULL                    COMMENT '消息唯一 ID（去重）',
    session_id      VARCHAR(64)     NULL                        COMMENT '会话 ID',
    sender_id       VARCHAR(64)     NULL                        COMMENT '发送人',
    msg_type        VARCHAR(20)     NULL                        COMMENT 'TEXT/IMAGE/FILE/SYSTEM',
    payload         TEXT            NULL                        COMMENT '消息 JSON',
    delivered       TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否已投递（用户上线已读）',
    delivered_at    DATETIME        NULL,
    expires_at      DATETIME        NULL                        COMMENT '过期时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_msg_id (msg_id),
    INDEX idx_user_undelivered (user_id, delivered, created_at),
    INDEX idx_session (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线消息持久化';

-- MessageDelivery 消息投递日志
DROP TABLE IF EXISTS message_delivery;
CREATE TABLE message_delivery (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    msg_id          VARCHAR(64)     NOT NULL,
    user_id         VARCHAR(64)     NOT NULL,
    channel         VARCHAR(20)     NOT NULL                    COMMENT 'WEBSOCKET/KAFKA/PUSH',
    status          VARCHAR(20)     NOT NULL                    COMMENT 'SENT/ACK/FAILED',
    retry_count     INT             NOT NULL DEFAULT 0,
    error_message   VARCHAR(512)    NULL,
    sent_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acked_at        DATETIME        NULL,

    INDEX idx_msg (msg_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息投递日志';

-- ============================================================
-- 3. 演示数据
-- ============================================================
USE cs_im;

INSERT INTO product (product_code, name, product_type, risk_level, min_amount, yield_rate, max_amount, period, description) VALUES
('FUND-001', '稳赢 30 天', 'FUND', 'LOW', 100.0000, 3.2000, 1000000.0000, 30, '30 天短期理财');

-- ============================================================
-- 4. 验证
-- ============================================================
SELECT 'cs_auth' AS db, COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'cs_auth'
UNION ALL
SELECT 'cs_im', COUNT(*) FROM information_schema.tables WHERE table_schema = 'cs_im'
UNION ALL
SELECT 'cs_message', COUNT(*) FROM information_schema.tables WHERE table_schema = 'cs_message';