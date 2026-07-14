-- 本地登录会话 + 多用户账本隔离
-- 执行前请确认已备份；会清空测试用 account/flow 数据

CREATE TABLE IF NOT EXISTS auth_token (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id       INT          NOT NULL,
  token_hash    CHAR(64)     NOT NULL COMMENT 'SHA-256(hex) of raw token',
  expires_at    DATETIME     NOT NULL,
  created_at    DATETIME     NOT NULL,
  last_used_at  DATETIME     NULL,
  revoked       TINYINT      NOT NULL DEFAULT 0,
  user_agent    VARCHAR(255) NULL,
  UNIQUE KEY uk_token_hash (token_hash),
  KEY idx_user_id (user_id),
  KEY idx_expires (expires_at)
) COMMENT '单端登录会话；客户端持有明文 token，库内只存哈希';

DELETE FROM flow;
DELETE FROM account;

-- 若列已存在会失败，按环境跳过对应语句即可
ALTER TABLE account
  ADD COLUMN user_id INT NOT NULL COMMENT '所属用户' AFTER id;
ALTER TABLE flow
  ADD COLUMN user_id INT NOT NULL COMMENT '所属用户' AFTER id;

CREATE INDEX idx_account_user ON account(user_id);
CREATE INDEX idx_flow_user ON flow(user_id);
