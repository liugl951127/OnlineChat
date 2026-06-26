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
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
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
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_msg_id (msg_id),
    INDEX idx_session_id (session_id),
    INDEX idx_sender_id (sender_id),
    INDEX idx_create_time (create_time),
    INDEX idx_session_time (session_id, create_time)
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
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_file_id (file_id),
    INDEX idx_uploader_id (uploader_id),
    INDEX idx_session_id (session_id),
    INDEX idx_scan_status (scan_status),
    INDEX idx_create_time (create_time)
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
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;