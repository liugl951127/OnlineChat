-- =====================================================
-- v2.2.80 增量 SQL: 公众号关注状态 + 设备号绑定
-- =====================================================
--
-- 适用场景:
--   - 已部署 v2.2.78 以下版本, 直接增量 ALTER
--   - 新部署请用 docs/db_init_all.sql (已包含本脚本内容)
--
-- 变更:
--   1. wechat_user + 3 字段 (subscribe_status / subscribe_checked_at / device_id)
--   2. wechat_user + 2 索引 (idx_subscribe / idx_device)
--   3. wechat_oauth_state 新表
--   4. wechat_subscribe_log 新表
--
-- 用法:
--   mysql -uroot -proot123 cs_auth < scripts/v2.2.80-wechat-subscribe.sql
-- =====================================================

USE cs_auth;

-- 1. wechat_user 加 3 字段
ALTER TABLE wechat_user
    ADD COLUMN IF NOT EXISTS subscribe_status TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '0=未知 1=已关注 2=未关注',
    ADD COLUMN IF NOT EXISTS subscribe_checked_at DATETIME NULL
        COMMENT '最近一次检查关注状态时间',
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(64) NULL
        COMMENT 'v2.2.80 设备号绑定, 用于设备号+公众号绑定';

-- 2. 加 2 索引 (IF NOT EXISTS 在 mariadb 10.5+ 支持)
ALTER TABLE wechat_user
    ADD INDEX IF NOT EXISTS idx_subscribe (subscribe_status),
    ADD INDEX IF NOT EXISTS idx_device (device_id);

-- 3. 新表 wechat_oauth_state
DROP TABLE IF EXISTS wechat_oauth_state;
CREATE TABLE wechat_oauth_state (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    state           VARCHAR(64) NOT NULL,
    scope           VARCHAR(64) NULL,
    redirect_uri    VARCHAR(512) NULL,
    device_id       VARCHAR(64) NULL,
    user_id         VARCHAR(64) NULL COMMENT '已登录用户刷新关注状态时存',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME NOT NULL,
    consumed        TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_state (state),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WechatOauthState OAuth state 防CSRF';

-- 4. 新表 wechat_subscribe_log
DROP TABLE IF EXISTS wechat_subscribe_log;
CREATE TABLE wechat_subscribe_log (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(64) NOT NULL,
    customer_id     VARCHAR(64) NULL,
    old_status      TINYINT(1) NULL,
    new_status      TINYINT(1) NOT NULL COMMENT '1=已关注 2=未关注',
    check_source    VARCHAR(64) NULL COMMENT 'OAUTH_CALLBACK / QRCODE / SCHEDULED',
    source_ip       VARCHAR(64) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_openid (openid),
    INDEX idx_customer (customer_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WechatSubscribeLog 关注状态变更日志';

-- 5. 验证
SELECT 'wechat_user 字段' AS check_name;
DESC wechat_user;

SELECT '新表' AS check_name;
SHOW TABLES LIKE 'wechat%';