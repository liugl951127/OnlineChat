-- =====================================================
-- V5.0.0  cs-im 升级：坐席 AI 助手 + 多媒体（屏幕共享/语音/Markdown）
-- =====================================================

-- 1) AI 推荐话术
CREATE TABLE IF NOT EXISTS ai_suggestion (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL                                COMMENT '会话 ID',
    customer_id         VARCHAR(64)     NOT NULL                                COMMENT '客户 ID',
    agent_username      VARCHAR(64)     NOT NULL                                COMMENT '坐席',
    trigger_message_id  BIGINT          NULL                                    COMMENT '触发消息（客户消息）',
    suggestion_type     VARCHAR(32)     NOT NULL DEFAULT 'REPLY'               COMMENT 'REPLY/KNOWLEDGE/FAQ/ACTION',
    content             TEXT            NOT NULL                                COMMENT '推荐内容',
    confidence          DECIMAL(5, 2)   NULL                                    COMMENT '置信度 0-100',
    sources             VARCHAR(512)    NULL                                    COMMENT '引用来源 (faq_id/knowledge_id)',
    used                TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '坐席是否采纳',
    rating              TINYINT         NULL                                    COMMENT '坐席评分 1-5',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_agent (agent_username),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐话术';

-- 2) AI 知识库（向量化）
CREATE TABLE IF NOT EXISTS ai_knowledge (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    title               VARCHAR(256)    NOT NULL                                COMMENT '知识标题',
    content             TEXT            NOT NULL                                COMMENT '知识内容',
    category            VARCHAR(32)     NOT NULL DEFAULT 'GENERAL'              COMMENT 'GENERAL/PRODUCT/POLICY/FAQ',
    tags                VARCHAR(256)    NULL                                    COMMENT '逗号分隔标签',
    embedding           BLOB            NULL                                    COMMENT '向量化（768 维 float[]）',
    embedding_model     VARCHAR(64)     NULL                                    COMMENT 'embedding 模型',
    view_count          INT             NOT NULL DEFAULT 0,
    helpful_count       INT             NOT NULL DEFAULT 0,
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL'              COMMENT 'MANUAL/FAQ_SYNC/IMPORTED',
    source_id           BIGINT          NULL                                    COMMENT '关联源 ID',
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'              COMMENT 'ACTIVE/ARCHIVED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_category (category),
    INDEX idx_source (source, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识库';

-- 3) 屏幕共享会话
CREATE TABLE IF NOT EXISTS screen_share_session (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL                                COMMENT '关联会话',
    initiator           VARCHAR(64)     NOT NULL                                COMMENT '发起方 (agent/customer)',
    peer                VARCHAR(64)     NOT NULL                                COMMENT '对方',
    status              VARCHAR(16)     NOT NULL DEFAULT 'INVITED'             COMMENT 'INVITED/ACTIVE/ENDED/REJECTED',
    sdp_offer           MEDIUMTEXT      NULL                                    COMMENT 'WebRTC offer',
    sdp_answer          MEDIUMTEXT      NULL                                    COMMENT 'WebRTC answer',
    started_at          DATETIME        NULL                                    COMMENT '开始时间',
    ended_at            DATETIME        NULL                                    COMMENT '结束时间',
    duration_sec        INT             NULL                                    COMMENT '总时长（秒）',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='屏幕共享会话';

-- 4) 语音消息
CREATE TABLE IF NOT EXISTS voice_message (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT          NOT NULL,
    from_id             VARCHAR(64)     NOT NULL,
    from_role           VARCHAR(32)     NOT NULL,
    audio_url           VARCHAR(512)    NOT NULL                                COMMENT 'OSS 音频 URL',
    duration_sec        INT             NOT NULL                                COMMENT '时长（秒）',
    file_size_kb        INT             NULL                                    COMMENT '文件大小（KB）',
    waveform_data       TEXT            NULL                                    COMMENT '波形数据 JSON',
    transcription       VARCHAR(1024)   NULL                                    COMMENT 'ASR 转写文本',
    transcription_status VARCHAR(16)    NOT NULL DEFAULT 'PENDING'             COMMENT 'PENDING/SUCCESS/FAILED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_session (session_id),
    INDEX idx_transcription (transcription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='语音消息';

-- 5) AI 推荐反馈（用于模型调优）
CREATE TABLE IF NOT EXISTS ai_feedback (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    suggestion_id       BIGINT          NOT NULL,
    agent_username      VARCHAR(64)     NOT NULL,
    feedback_type       VARCHAR(16)     NOT NULL                                COMMENT 'USED/SKIPPED/MODIFIED/RATED',
    rating              TINYINT         NULL                                    COMMENT '1-5 星',
    modified_content    TEXT            NULL                                    COMMENT '坐席修改后的内容',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_suggestion (suggestion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 推荐反馈';

-- =====================================================
-- 演示数据：5 条 AI 知识库（产品 + 政策 + FAQ）
-- =====================================================
INSERT INTO ai_knowledge (title, content, category, tags, view_count, helpful_count) VALUES
('理财产品起购金额', '稳赢 30 天起购 100 元；稳赢 90 天起购 1000 元；成长基金起购 1000 元；国债 5 年起购 1000 元；平安出行险 50 元起。', 'PRODUCT', '起购,理财,基金,保险', 0, 0),
('理财产品赎回规则', '活期类可随时赎回；定期类需持有到期；基金类 T+1 确认赎回。', 'PRODUCT', '赎回,理财,基金', 0, 0),
('风险评估有效期', '风险评估结果有效期为 1 年。过期后需重新评估才能购买新金融产品。', 'POLICY', '风险评估,KYC,合规', 0, 0),
('KYC 实名认证流程', 'KYC 认证包括 7 步：身份证 OCR、活体检测、人脸比对、视频双录、坐席审核、银行卡四要素。整个流程约 5 分钟。', 'POLICY', 'KYC,实名,认证', 0, 0),
('投诉处理时效', '普通投诉 24 小时内响应；紧急投诉 4 小时内响应。', 'POLICY', '投诉,SLA,工单', 0, 0);

INSERT INTO ai_knowledge (title, content, category, tags, view_count, helpful_count) VALUES
('账户余额查询', '客户问"我还有多少钱"/"余额"时，引导客户查看"我的"页面或调用 /auth/me 接口。', 'FAQ', '余额,账户,查询', 0, 0),
('转账失败原因', '常见原因：1) 余额不足；2) 收款方信息错误；3) 单笔超 5 万限额；4) 银行卡未 KYC 认证。', 'FAQ', '转账,失败,限额', 0, 0),
('密码忘记怎么办', '点击登录页"忘记密码"，通过预留手机号验证码重置。需要 KYC 已认证。', 'FAQ', '密码,忘记,重置', 0, 0);