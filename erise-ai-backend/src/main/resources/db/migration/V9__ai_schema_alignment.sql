SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_chat_message'
              AND column_name = 'confidence'
        ),
        'SELECT 1',
        'ALTER TABLE ai_chat_message ADD COLUMN confidence DOUBLE NULL AFTER provider_code'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_chat_message'
              AND column_name = 'refused_reason'
        ),
        'SELECT 1',
        'ALTER TABLE ai_chat_message ADD COLUMN refused_reason VARCHAR(255) NULL AFTER confidence'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_chat_message'
              AND column_name = 'citations_json'
        ),
        'SELECT 1',
        'ALTER TABLE ai_chat_message ADD COLUMN citations_json TEXT NULL AFTER refused_reason'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_chat_message'
              AND column_name = 'used_tools_json'
        ),
        'SELECT 1',
        'ALTER TABLE ai_chat_message ADD COLUMN used_tools_json TEXT NULL AFTER citations_json'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_chat_message'
              AND column_name = 'answer_source'
        ),
        'SELECT 1',
        'ALTER TABLE ai_chat_message ADD COLUMN answer_source VARCHAR(64) NULL AFTER used_tools_json'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_message_citation'
              AND column_name = 'section_path'
        ),
        'SELECT 1',
        'ALTER TABLE ai_message_citation ADD COLUMN section_path VARCHAR(255) NULL AFTER page_no'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ea_ai_temp_file'
              AND column_name = 'project_id'
        ),
        'SELECT 1',
        'ALTER TABLE ea_ai_temp_file ADD COLUMN project_id BIGINT NULL AFTER owner_user_id'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ea_ai_temp_file'
              AND column_name = 'index_status'
        ),
        'SELECT 1',
        'ALTER TABLE ea_ai_temp_file ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' AFTER parse_status'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ea_ai_temp_file'
              AND column_name = 'last_error'
        ),
        'SELECT 1',
        'ALTER TABLE ea_ai_temp_file ADD COLUMN last_error VARCHAR(1000) NULL AFTER index_status'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ea_ai_temp_file'
              AND column_name = 'retry_count'
        ),
        'SELECT 1',
        'ALTER TABLE ea_ai_temp_file ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER last_error'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE ea_ai_temp_file
    MODIFY COLUMN parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
