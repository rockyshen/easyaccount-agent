-- SSE 对话断点续传：流元数据 + 事件日志（方案 A）
-- 库：yd_jz；可重复执行（IF NOT EXISTS）

CREATE TABLE IF NOT EXISTS chat_stream (
  stream_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
  user_id         INT          NOT NULL,
  status          VARCHAR(16)  NOT NULL COMMENT 'running|completed|failed|cancelled',
  full_text       MEDIUMTEXT   NULL,
  last_event_id   BIGINT       NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  expire_at       DATETIME     NOT NULL,
  KEY idx_chat_stream_user (user_id),
  KEY idx_chat_stream_expire (expire_at),
  KEY idx_chat_stream_user_status (user_id, status)
) COMMENT '一轮 SSE 对话生成流';

CREATE TABLE IF NOT EXISTS chat_stream_event (
  stream_id       VARCHAR(64)  NOT NULL,
  event_id        BIGINT       NOT NULL,
  event_name      VARCHAR(32)  NOT NULL,
  data_json       TEXT         NOT NULL,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (stream_id, event_id),
  KEY idx_chat_stream_event_stream (stream_id)
) COMMENT 'SSE 事件日志，供断点续传重放';
