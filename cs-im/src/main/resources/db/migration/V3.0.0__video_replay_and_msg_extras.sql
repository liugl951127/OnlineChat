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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 问答';