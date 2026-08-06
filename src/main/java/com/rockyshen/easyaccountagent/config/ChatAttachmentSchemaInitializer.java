package com.rockyshen.easyaccountagent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
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
                  id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
                  user_id            INT          NOT NULL,
                  kind               VARCHAR(16)  NOT NULL DEFAULT 'image',
                  mime_type          VARCHAR(64)  NOT NULL,
                  size_bytes         BIGINT       NOT NULL,
                  width              INT          NULL,
                  height             INT          NULL,
                  storage_path       VARCHAR(512) NOT NULL,
                  thumb_storage_path VARCHAR(512) NULL,
                  thumb_width        INT          NULL,
                  thumb_height       INT          NULL,
                  referenced         TINYINT(1)   NOT NULL DEFAULT 0,
                  created_at         DATETIME     NOT NULL,
                  expires_at         DATETIME     NOT NULL,
                  referenced_at      DATETIME     NULL,
                  KEY idx_chat_attachment_user (user_id),
                  KEY idx_chat_attachment_expires (expires_at)
                );
                """;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(ddl.trim());
            ensureColumn(conn, "chat_attachment", "thumb_storage_path", "VARCHAR(512) NULL");
            ensureColumn(conn, "chat_attachment", "thumb_width", "INT NULL");
            ensureColumn(conn, "chat_attachment", "thumb_height", "INT NULL");
            log.info("[ChatAttachment] schema ready");
        } catch (Exception e) {
            log.error("[ChatAttachment] 自动建表失败，请手动执行 scripts/chat_attachment_ddl.sql: {}",
                    e.toString());
        }
    }

    private static void ensureColumn(Connection conn, String table, String column, String definition)
            throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("[ChatAttachment] added column {}.{}", table, column);
        }
    }
}
