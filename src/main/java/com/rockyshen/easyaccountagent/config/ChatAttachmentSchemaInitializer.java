package com.rockyshen.easyaccountagent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 确保 chat_attachment 表存在（与 scripts/chat_attachment_ddl.sql 一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAttachmentSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        String ddl = """
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
                );
                """;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(ddl.trim());
            log.info("[ChatAttachment] schema ready");
        } catch (Exception e) {
            log.error("[ChatAttachment] 自动建表失败，请手动执行 scripts/chat_attachment_ddl.sql: {}",
                    e.toString());
        }
    }
}
