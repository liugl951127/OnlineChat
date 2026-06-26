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