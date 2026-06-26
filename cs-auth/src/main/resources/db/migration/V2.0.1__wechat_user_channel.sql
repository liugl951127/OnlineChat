-- V2.0.1: cs-auth 加 channel 列（WECHAT_OA / WECHAT_WORK / WECHAT_MINI / LOCAL）
ALTER TABLE wechat_user ADD COLUMN channel VARCHAR(32) NULL AFTER role;