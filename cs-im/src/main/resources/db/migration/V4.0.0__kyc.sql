-- =====================================================
-- V4.0.0  cs-im 升级：完整 KYC（身份认证 + 视频双录 + 银行卡四要素）
-- =====================================================

-- 1) KYC 申请单
CREATE TABLE IF NOT EXISTS kyc_application (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_no      VARCHAR(64)     NOT NULL                                COMMENT '申请单号',
    customer_id         VARCHAR(64)     NOT NULL                                COMMENT '客户 ID',
    status              VARCHAR(32)     NOT NULL DEFAULT 'INIT'                  COMMENT 'INIT/OCR_UPLOADED/LIVENESS_PASSED/FACE_MATCHED/VIDEO_RECORDED/AUDITING/APPROVED/REJECTED/BANK_BINDING/COMPLETED',

    -- 身份证 OCR
    id_card_no          VARCHAR(32)     NULL                                    COMMENT '身份证号（加密存储）',
    id_card_name        VARCHAR(64)     NULL                                    COMMENT '身份证姓名',
    id_card_gender      VARCHAR(8)      NULL                                    COMMENT 'M/F',
    id_card_nation      VARCHAR(32)     NULL                                    COMMENT '民族',
    id_card_birth       VARCHAR(16)     NULL                                    COMMENT '生日 YYYY-MM-DD',
    id_card_address     VARCHAR(256)    NULL                                    COMMENT '地址',
    id_card_issue       VARCHAR(64)     NULL                                    COMMENT '签发机关',
    id_card_validity    VARCHAR(32)     NULL                                    COMMENT '有效期',
    id_card_front_img   VARCHAR(512)    NULL                                    COMMENT '身份证正面图',
    id_card_back_img    VARCHAR(512)    NULL                                    COMMENT '身份证反面图',
    ocr_raw_json        TEXT            NULL                                    COMMENT 'OCR 原始 JSON',

    -- 人脸比对
    face_img_url        VARCHAR(512)    NULL                                    COMMENT '活体照片 URL',
    face_match_score    DECIMAL(5, 2)  NULL                                    COMMENT '相似度 0-100',
    face_match_passed   TINYINT(1)      NULL                                    COMMENT '是否通过',

    -- 活体检测
    liveness_actions    VARCHAR(128)    NULL                                    COMMENT '活体动作序列',
    liveness_score      DECIMAL(5, 2)  NULL                                    COMMENT '活体分数',
    liveness_passed     TINYINT(1)      NULL                                    COMMENT '是否通过',

    -- 视频双录
    risk_statement_id   BIGINT          NULL                                    COMMENT '风险声明 ID',
    video_url           VARCHAR(512)    NULL                                    COMMENT '双录视频 URL',
    video_duration_sec  INT             NULL                                    COMMENT '视频时长（秒）',
    video_recorded_at   DATETIME        NULL                                    COMMENT '录制完成时间',

    -- 审核
    auditor_username    VARCHAR(64)     NULL                                    COMMENT '审核员',
    audit_score         DECIMAL(5, 2)  NULL                                    COMMENT '审核评分',
    audit_remark        VARCHAR(512)    NULL                                    COMMENT '审核备注',
    audited_at          DATETIME        NULL,

    -- 银行卡
    bank_card_no        VARCHAR(32)     NULL                                    COMMENT '卡号（加密）',
    bank_card_name      VARCHAR(64)     NULL                                    COMMENT '持卡人姓名',
    bank_card_mobile    VARCHAR(32)     NULL                                    COMMENT '预留手机号',
    bank_card_bind_at   DATETIME        NULL                                    COMMENT '绑卡时间',

    -- 风控
    risk_level          VARCHAR(16)     NULL                                    COMMENT 'LOW/MID/HIGH',
    blacklist_hit       TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否命中黑名单',

    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at        DATETIME        NULL                                    COMMENT 'KYC 完成时间',
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_application_no (application_no),
    INDEX idx_customer (customer_id),
    INDEX idx_status (status),
    INDEX idx_auditor (auditor_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 申请单';

-- 2) 风险声明库（监管要求的固定话术）
CREATE TABLE IF NOT EXISTS kyc_risk_statement (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64)     NOT NULL                                COMMENT '声明代码',
    title               VARCHAR(256)    NOT NULL                                COMMENT '声明标题',
    content             TEXT            NOT NULL                                COMMENT '声明全文（客户朗读）',
    category            VARCHAR(32)     NOT NULL DEFAULT 'GENERAL'              COMMENT 'GENERAL/INVESTMENT/INSURANCE',
    required_duration_sec INT           NOT NULL DEFAULT 10                    COMMENT '要求朗读时长',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED'            COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    UNIQUE KEY uk_code (code),
    INDEX idx_status (status),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险声明库';

-- 3) 双录视频记录（分段，每段一条）
CREATE TABLE IF NOT EXISTS kyc_video_record (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_id      BIGINT          NOT NULL                                COMMENT '申请单 ID',
    statement_id        BIGINT          NOT NULL                                COMMENT '声明 ID',
    segment_no          INT             NOT NULL DEFAULT 1                     COMMENT '段序号',
    video_url           VARCHAR(512)    NOT NULL                                COMMENT '视频 URL',
    duration_sec        INT             NOT NULL                                COMMENT '时长（秒）',
    file_size_kb        INT             NULL                                    COMMENT '文件大小（KB）',
    checksum            VARCHAR(64)     NULL                                    COMMENT 'SHA-256 校验',
    recorded_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 双录视频';

-- 4) 客户银行卡
CREATE TABLE IF NOT EXISTS bank_card (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    customer_id         VARCHAR(64)     NOT NULL,
    card_no_enc         VARCHAR(256)    NOT NULL                                COMMENT '卡号（加密）',
    card_no_masked      VARCHAR(32)     NOT NULL                                COMMENT '卡号掩码（如 **** **** **** 1234）',
    card_name           VARCHAR(64)     NOT NULL                                COMMENT '持卡人姓名',
    bank_code           VARCHAR(16)     NULL                                    COMMENT '银行代码（ICBC/CCB/ABC 等）',
    bank_name           VARCHAR(64)     NULL                                    COMMENT '银行名称',
    card_type           VARCHAR(16)     NOT NULL DEFAULT 'DEBIT'                COMMENT 'DEBIT/CREDIT',
    mobile              VARCHAR(32)     NULL                                    COMMENT '预留手机号',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否默认卡',
    verified            TINYINT(1)      NOT NULL DEFAULT 0                     COMMENT '是否四要素验证通过',
    verified_at         DATETIME        NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'BOUND'                COMMENT 'BOUND/UNBOUND/FROZEN',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_customer (customer_id),
    INDEX idx_card_masked (card_no_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户绑卡';

-- 5) KYC 审核日志
CREATE TABLE IF NOT EXISTS kyc_audit_log (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    application_id      BIGINT          NOT NULL,
    auditor             VARCHAR(64)     NOT NULL,
    action              VARCHAR(32)     NOT NULL                                COMMENT 'SUBMIT/OCR/LIVENESS/FACE/VIDEO/AUDIT/APPROVE/REJECT/BIND',
    from_status         VARCHAR(32)     NULL,
    to_status           VARCHAR(32)     NULL,
    detail              VARCHAR(512)    NULL,
    ip                  VARCHAR(64)     NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_application (application_id),
    INDEX idx_auditor (auditor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 审核日志';

-- 6) KYC 黑名单
CREATE TABLE IF NOT EXISTS kyc_blacklist (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    id_card_no_enc      VARCHAR(256)    NOT NULL                                COMMENT '身份证号（加密）',
    customer_id         VARCHAR(64)     NULL                                    COMMENT '关联客户',
    reason              VARCHAR(256)    NOT NULL                                COMMENT '加入原因',
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL'              COMMENT 'MANUAL/SYSTEM/POLICE',
    expires_at          DATETIME        NULL                                    COMMENT '过期时间（永久则 NULL）',
    created_by          VARCHAR(64)     NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,

    INDEX idx_idcard (id_card_no_enc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KYC 黑名单';

-- =====================================================
-- 演示数据：风险声明库（5 段监管要求的标准声明）
-- =====================================================
INSERT INTO kyc_risk_statement (code, title, content, category, required_duration_sec, sort_order) VALUES
('RS-INVEST-001', '投资者适当性声明', '本人确认：本人的风险承受能力等级与本次投资产品的风险等级相匹配。本人理解投资有风险，过往业绩不代表未来表现。本人具备相应的投资经验和资金实力，能够承担相应的投资风险。', 'INVESTMENT', 15, 1),
('RS-INVEST-002', '资金来源合法声明', '本人确认：本次投资的资金来源合法，不属于洗钱或恐怖融资。本人不是代表他人或单位进行投资。本人理解反洗钱法规的要求，并承诺配合相关尽职调查。', 'INVESTMENT', 12, 2),
('RS-INSURE-001', '投保意愿声明', '本人确认：本投保申请是基于本人真实意愿。本人已仔细阅读并理解保险条款、产品说明书和风险提示。本人自愿购买本保险产品。', 'INSURANCE', 10, 3),
('RS-GENERAL-001', '信息真实性声明', '本人确认：所提供的所有身份信息、联系方式和交易信息均真实、准确、完整、有效。如有变更将及时更新。', 'GENERAL', 8, 4),
('RS-FUND-001', '基金交易特别声明', '本人确认：已阅读基金合同、招募说明书，了解基金产品的风险等级、费率结构和赎回规则。本人理解基金投资可能面临本金损失风险。', 'INVESTMENT', 15, 5)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;