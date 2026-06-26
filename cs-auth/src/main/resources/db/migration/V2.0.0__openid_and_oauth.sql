-- v1.8.0 v2.0.0: add openid/ww_userid columns for OAuth (wechat OA + wechat work)
ALTER TABLE wechat_user ADD COLUMN openid VARCHAR(64) NULL AFTER unionid;
ALTER TABLE wechat_user ADD COLUMN ww_userid VARCHAR(64) NULL AFTER openid;
ALTER TABLE wechat_user ADD INDEX idx_openid (openid);
ALTER TABLE wechat_user ADD INDEX idx_ww_userid (ww_userid);