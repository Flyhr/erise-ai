package com.erise.ai.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class FlywayMigrationMySqlTest {

    @Test
    void migratesFreshSchemaThroughV19AndPreservesExpectedColumns() throws SQLException {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required to run the MySQL Flyway migration regression test"
        );

        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("erise_ai_test")) {
            mysql.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            flyway.migrate();

            assertThat(flyway.info().current()).isNotNull();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword()
            )) {
                assertThat(columnDefaultsFor(connection, "ea_file"))
                        .containsKeys("review_status", "review_comment", "reviewed_by_user_id", "reviewed_at", "archived");

                assertThat(columnDefaultsFor(connection, "ai_chat_message"))
                        .containsKeys("confidence", "refused_reason", "citations_json", "used_tools_json", "answer_source");

                assertThat(columnDefaultsFor(connection, "ai_request_log"))
                        .containsKeys("user_id", "org_id", "project_id", "answer_source", "message_status", "total_token_count", "latency_ms");

                assertThat(columnDefaultsFor(connection, "ai_model_config"))
                        .containsKeys("is_default", "input_price_per_million", "output_price_per_million", "currency_code");

                assertThat(columnDefaultsFor(connection, "ai_message_citation"))
                        .containsKey("section_path");

                assertThat(columnDefaultsFor(connection, "ai_action_log"))
                        .containsKeys("action_code", "action_status", "success_flag");

                assertThat(columnDefaultsFor(connection, "ai_message_feedback"))
                        .containsKeys("feedback_type", "feedback_note");

                assertThat(columnDefaultsFor(connection, "approval_request"))
                        .containsKeys("action_code", "status", "risk_level", "plan_summary");

                assertThat(columnDefaultsFor(connection, "admin_action_request"))
                        .containsKeys("approval_request_id", "action_code", "action_status");

                assertThat(columnDefaultsFor(connection, "mcp_access_log"))
                        .containsKeys("method", "tool_name", "resource_uri", "error_code");

                assertThat(columnDefaultsFor(connection, "n8n_event_log"))
                        .containsKeys("event_type", "workflow_hint", "target_url", "success_flag");

                assertThat(columnDefaultsFor(connection, "automation_webhook_log"))
                        .containsKeys("workflow_code", "event_type", "request_payload_json");

                assertThat(columnDefaultsFor(connection, "ea_user_notification"))
                        .containsKey("broadcast_flag");

                Map<String, String> tempFileColumns = columnDefaultsFor(connection, "ea_ai_temp_file");
                assertThat(tempFileColumns)
                        .containsKeys("project_id", "index_status", "last_error", "retry_count", "parse_status");
                assertThat(tempFileColumns.get("parse_status")).isEqualTo("PENDING");
                assertThat(tempFileColumns.get("index_status")).isEqualTo("PENDING");

                assertThat(columnDefaultsFor(connection, "ea_rag_sparse_term"))
                        .containsKeys("term", "field_code", "term_freq", "doc_len");
            }
        }
    }

    @Test
    void migratesLegacyAiSchemaThroughV19AndBackfillsMissingColumns() throws SQLException {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required to run the MySQL Flyway migration regression test"
        );

        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("erise_ai_test")) {
            mysql.start();

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword()
            )) {
                executeSql(connection, """
                        CREATE TABLE ai_chat_message (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            session_id BIGINT NOT NULL,
                            user_id BIGINT NOT NULL,
                            role VARCHAR(32) NOT NULL,
                            content TEXT NOT NULL,
                            content_format VARCHAR(32) NOT NULL DEFAULT 'text',
                            message_status VARCHAR(32) NOT NULL DEFAULT 'success',
                            sequence_no INT NOT NULL,
                            model_code VARCHAR(128) NULL,
                            provider_code VARCHAR(64) NULL,
                            prompt_tokens INT NULL,
                            completion_tokens INT NULL,
                            total_tokens INT NULL,
                            latency_ms INT NULL,
                            error_code VARCHAR(64) NULL,
                            error_message VARCHAR(500) NULL,
                            request_id VARCHAR(128) NULL,
                            parent_message_id BIGINT NULL,
                            created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (id)
                        )
                        """);
                executeSql(connection, """
                        CREATE TABLE ai_message_citation (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            message_id BIGINT NOT NULL,
                            session_id BIGINT NOT NULL,
                            user_id BIGINT NOT NULL,
                            position_no INT NOT NULL DEFAULT 0,
                            source_type VARCHAR(32) NOT NULL,
                            source_id BIGINT NOT NULL,
                            source_title VARCHAR(255) NOT NULL,
                            snippet TEXT NULL,
                            page_no INT NULL,
                            score DOUBLE NULL,
                            url VARCHAR(1000) NULL,
                            created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            PRIMARY KEY (id)
                        )
                        """);
                executeSql(connection, """
                        CREATE TABLE ea_ai_temp_file (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            session_id BIGINT NOT NULL,
                            owner_user_id BIGINT NOT NULL,
                            file_name VARCHAR(255) NOT NULL,
                            file_ext VARCHAR(32) NOT NULL,
                            mime_type VARCHAR(128),
                            file_size BIGINT NOT NULL DEFAULT 0,
                            storage_bucket VARCHAR(128) NOT NULL,
                            storage_key VARCHAR(255) NOT NULL,
                            plain_text LONGTEXT,
                            parse_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
                            created_by BIGINT,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_by BIGINT,
                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            deleted TINYINT NOT NULL DEFAULT 0
                        )
                        """);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))
                    .load();

            flyway.migrate();

            assertThat(flyway.info().current()).isNotNull();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword()
            )) {
                assertThat(columnDefaultsFor(connection, "ea_file"))
                        .containsKeys("review_status", "review_comment", "reviewed_by_user_id", "reviewed_at", "archived");

                assertThat(columnDefaultsFor(connection, "ai_chat_message"))
                        .containsKeys("confidence", "refused_reason", "citations_json", "used_tools_json", "answer_source");

                assertThat(columnDefaultsFor(connection, "ai_request_log"))
                        .containsKeys("user_id", "org_id", "project_id", "answer_source", "message_status", "total_token_count", "latency_ms");

                assertThat(columnDefaultsFor(connection, "ai_model_config"))
                        .containsKeys("is_default", "input_price_per_million", "output_price_per_million", "currency_code");

                assertThat(columnDefaultsFor(connection, "ai_message_citation"))
                        .containsKey("section_path");

                assertThat(columnDefaultsFor(connection, "ai_action_log"))
                        .containsKeys("action_code", "action_status", "success_flag");

                assertThat(columnDefaultsFor(connection, "ai_message_feedback"))
                        .containsKeys("feedback_type", "feedback_note");

                assertThat(columnDefaultsFor(connection, "approval_request"))
                        .containsKeys("action_code", "status", "risk_level", "plan_summary");

                assertThat(columnDefaultsFor(connection, "admin_action_request"))
                        .containsKeys("approval_request_id", "action_code", "action_status");

                assertThat(columnDefaultsFor(connection, "mcp_access_log"))
                        .containsKeys("method", "tool_name", "resource_uri", "error_code");

                assertThat(columnDefaultsFor(connection, "n8n_event_log"))
                        .containsKeys("event_type", "workflow_hint", "target_url", "success_flag");

                assertThat(columnDefaultsFor(connection, "automation_webhook_log"))
                        .containsKeys("workflow_code", "event_type", "request_payload_json");

                assertThat(columnDefaultsFor(connection, "ea_user_notification"))
                        .containsKey("broadcast_flag");

                Map<String, String> tempFileColumns = columnDefaultsFor(connection, "ea_ai_temp_file");
                assertThat(tempFileColumns)
                        .containsKeys("project_id", "index_status", "last_error", "retry_count", "parse_status");
                assertThat(tempFileColumns.get("parse_status")).isEqualTo("PENDING");
                assertThat(tempFileColumns.get("index_status")).isEqualTo("PENDING");

                assertThat(columnDefaultsFor(connection, "ea_rag_sparse_term"))
                        .containsKeys("term", "field_code", "term_freq", "doc_len");
            }
        }
    }

    private Map<String, String> columnDefaultsFor(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT COLUMN_NAME, COLUMN_DEFAULT
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """;

        Map<String, String> defaults = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    defaults.put(resultSet.getString("COLUMN_NAME"), resultSet.getString("COLUMN_DEFAULT"));
                }
            }
        }
        return defaults;
    }

    private void executeSql(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
