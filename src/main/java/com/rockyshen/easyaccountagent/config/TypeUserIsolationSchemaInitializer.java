package com.rockyshen.easyaccountagent.config;

import com.rockyshen.easyaccountagent.service.TypeSeedService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 确保 type.user_id、type_template 存在，并幂等补齐全局 action 与模板种子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TypeUserIsolationSchemaInitializer {

    private final DataSource dataSource;
    private final TypeSeedService typeSeedService;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            ensureColumn(conn, "type", "user_id", "INT NULL COMMENT '所属用户'");
            ensureIndex(st, "type", "idx_type_user", "CREATE INDEX idx_type_user ON `type`(user_id)");
            ensureIndex(st, "type", "idx_type_user_action",
                    "CREATE INDEX idx_type_user_action ON `type`(user_id, action_id)");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS type_template (
                      id            INT PRIMARY KEY AUTO_INCREMENT,
                      t_name        VARCHAR(50)  NOT NULL,
                      parent        INT          NOT NULL DEFAULT -1,
                      action_handle INT          NOT NULL,
                      sort_order    INT          NOT NULL DEFAULT 0,
                      KEY idx_type_template_parent (parent),
                      KEY idx_type_template_handle (action_handle)
                    )
                    """);
            log.info("[Onboarding] type user isolation schema ready");
        } catch (Exception e) {
            log.error("[Onboarding] schema init failed, please run scripts/alter_type_user_isolation.sql: {}",
                    e.toString());
            return;
        }

        try {
            typeSeedService.ensureGlobalActions();
            typeSeedService.ensureTypeTemplates();
        } catch (Exception e) {
            log.error("[Onboarding] seed actions/templates failed: {}", e.toString());
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
            st.execute("ALTER TABLE `" + table + "` ADD COLUMN " + column + " " + definition);
            log.info("[Onboarding] added column {}.{}", table, column);
        }
    }

    private static void ensureIndex(Statement st, String table, String indexName, String ddl) {
        try {
            st.execute(ddl);
            log.info("[Onboarding] ensured index {}.{}", table, indexName);
        } catch (Exception e) {
            // 已存在等错误可忽略
            log.debug("[Onboarding] index {} on {}: {}", indexName, table, e.toString());
        }
    }
}
