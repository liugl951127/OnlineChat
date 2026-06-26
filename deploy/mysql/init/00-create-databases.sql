-- =====================================================
-- 初始化脚本：创建 4 个业务数据库（cs_auth/cs_im/cs_trade/cs_chat）
-- MySQL 8.x / utf8mb4
-- =====================================================

CREATE DATABASE IF NOT EXISTS cs_auth
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cs_im
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cs_trade
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Nacos 数据库（如启用 Nacos + MySQL）
CREATE DATABASE IF NOT EXISTS nacos_config
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 演示账号
CREATE USER IF NOT EXISTS 'cs_app'@'%' IDENTIFIED BY 'cs_app_password_2024';
GRANT ALL PRIVILEGES ON cs_auth.* TO 'cs_app'@'%';
GRANT ALL PRIVILEGES ON cs_im.* TO 'cs_app'@'%';
GRANT ALL PRIVILEGES ON cs_trade.* TO 'cs_app'@'%';
FLUSH PRIVILEGES;