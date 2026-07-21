-- MysqlSaver 表结构参考（与 spring-ai-alibaba-graph-core 1.1.2.0 一致）
-- 生产若希望 DDL 与应用解耦：可先手工执行本脚本，再将 CreateOption 改为 CREATE_NONE（需改代码）。
-- 默认部署无需执行：应用启动时 CREATE_IF_NOT_EXISTS 会自动建表。

CREATE TABLE IF NOT EXISTS GRAPH_THREAD (
   thread_id VARCHAR(36) PRIMARY KEY,
   thread_name VARCHAR(255),
   is_released BOOLEAN DEFAULT FALSE NOT NULL
);

-- 索引已存在时会报错，可忽略
CREATE UNIQUE INDEX IDX_GRAPH_THREAD_NAME_RELEASED
  ON GRAPH_THREAD(thread_name, is_released);

CREATE TABLE IF NOT EXISTS GRAPH_CHECKPOINT (
   checkpoint_id VARCHAR(36) PRIMARY KEY,
   thread_id VARCHAR(36) NOT NULL,
   node_id VARCHAR(255),
   next_node_id VARCHAR(255),
   state_data JSON NOT NULL,
   saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

   CONSTRAINT GRAPH_FK_THREAD
       FOREIGN KEY(thread_id)
       REFERENCES GRAPH_THREAD(thread_id)
       ON DELETE CASCADE
);
