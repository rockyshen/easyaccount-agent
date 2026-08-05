-- 聊天图片附件元数据（文件本体落本地目录，见 easyaccount.attachments.storage-dir）
-- 库：yd_jz；可重复执行（IF NOT EXISTS）

CREATE TABLE IF NOT EXISTS chat_attachment (
  id              VARCHAR(64)  NOT NULL PRIMARY KEY,
  user_id         INT          NOT NULL,
  kind            VARCHAR(16)  NOT NULL DEFAULT 'image',
  mime_type       VARCHAR(64)  NOT NULL,
  size_bytes      BIGINT       NOT NULL,
  width           INT          NULL,
  height          INT          NULL,
  storage_path    VARCHAR(512) NOT NULL,
  referenced      TINYINT(1)   NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL,
  expires_at      DATETIME     NOT NULL,
  referenced_at   DATETIME     NULL,
  KEY idx_chat_attachment_user (user_id),
  KEY idx_chat_attachment_expires (expires_at)
) COMMENT '聊天附件元数据';
