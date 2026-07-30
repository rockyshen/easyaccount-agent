package com.rockyshen.easyaccountagent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 确保 chat_stream / chat_stream_event 表存在（与 scripts/chat_stream_ddl.sql 一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_stream (
                  stream_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
                  user_id         INT          NOT NULL,
                  status          VARCHAR(16)  NOT NULL,
                  full_text       MEDIUMTEXT   NULL,
                  last_event_id   BIGINT       NOT NULL DEFAULT 0,
                  created_at      DATETIME     NOT NULL,
                  updated_at      DATETIME     NOT NULL,
                  expire_at       DATETIME     NOT NULL,
                  KEY idx_chat_stream_user (user_id),
                  KEY idx_chat_stream_expire (expire_at),
                  KEY idx_chat_stream_user_status (user_id, status)
                );

                CREATE TABLE IF NOT EXISTS chat_stream_event (
                  stream_id       VARCHAR(64)  NOT NULL,
                  event_id        BIGINT       NOT NULL,
                  event_name      VARCHAR(32)  NOT NULL,
                  data_json       TEXT         NOT NULL,
                  created_at      DATETIME     NOT NULL,
                  PRIMARY KEY (stream_id, event_id),
                  KEY idx_chat_stream_event_stream (stream_id)
                );
                """;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            for (String stmt : ddl.split(";")) {
                String sql = stmt.trim();
                if (!sql.isEmpty()) {
                    st.execute(sql);
                }
            }
            log.info("[ChatStream] schema ready");
        } catch (Exception e) {
            log.error("[ChatStream] 自动建表失败，请手动执行 scripts/chat_stream_ddl.sql: {}", e.toString());
        }
    }
}
