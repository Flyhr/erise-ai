SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_request_log'
              AND column_name = 'user_id'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 AFTER session_id'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'org_id'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN org_id BIGINT NOT NULL DEFAULT 0 AFTER user_id'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'project_id'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN project_id BIGINT NULL AFTER org_id'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'answer_source'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN answer_source VARCHAR(64) NULL AFTER response_payload_json'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'message_status'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN message_status VARCHAR(32) NULL AFTER answer_source'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'total_token_count'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN total_token_count INT NULL AFTER output_token_count'
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
              AND table_name = 'ai_request_log'
              AND column_name = 'latency_ms'
        ),
        'SELECT 1',
        'ALTER TABLE ai_request_log ADD COLUMN latency_ms INT NULL AFTER total_token_count'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ai_request_log r
LEFT JOIN ai_chat_session s ON s.id = r.session_id
SET r.user_id = COALESCE(NULLIF(r.user_id, 0), s.user_id, 0),
    r.org_id = COALESCE(NULLIF(r.org_id, 0), s.org_id, 0),
    r.project_id = COALESCE(r.project_id, s.project_id)
WHERE r.user_id = 0
   OR r.org_id = 0
   OR r.project_id IS NULL;

UPDATE ai_request_log r
LEFT JOIN ai_chat_message m ON m.id = r.assistant_message_id
SET r.answer_source = COALESCE(r.answer_source, m.answer_source),
    r.message_status = COALESCE(r.message_status, m.message_status)
WHERE r.answer_source IS NULL
   OR r.message_status IS NULL;

UPDATE ai_request_log
SET total_token_count = COALESCE(total_token_count, COALESCE(input_token_count, 0) + COALESCE(output_token_count, 0))
WHERE total_token_count IS NULL
  AND (input_token_count IS NOT NULL OR output_token_count IS NOT NULL);

UPDATE ai_request_log
SET latency_ms = COALESCE(latency_ms, duration_ms)
WHERE latency_ms IS NULL
  AND duration_ms IS NOT NULL;
