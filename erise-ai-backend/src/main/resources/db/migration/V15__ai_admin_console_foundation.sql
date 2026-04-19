SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_model_config'
              AND column_name = 'is_default'
        ),
        'SELECT 1',
        'ALTER TABLE ai_model_config ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 AFTER enabled'
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
              AND table_name = 'ai_model_config'
              AND column_name = 'input_price_per_million'
        ),
        'SELECT 1',
        'ALTER TABLE ai_model_config ADD COLUMN input_price_per_million DECIMAL(12,4) NULL AFTER max_context_tokens'
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
              AND table_name = 'ai_model_config'
              AND column_name = 'output_price_per_million'
        ),
        'SELECT 1',
        'ALTER TABLE ai_model_config ADD COLUMN output_price_per_million DECIMAL(12,4) NULL AFTER input_price_per_million'
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
              AND table_name = 'ai_model_config'
              AND column_name = 'currency_code'
        ),
        'SELECT 1',
        'ALTER TABLE ai_model_config ADD COLUMN currency_code VARCHAR(16) NOT NULL DEFAULT ''USD'' AFTER output_price_per_million'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ai_model_config
SET is_default = 0
WHERE is_default IS NULL;

UPDATE ai_model_config
SET is_default = 1
WHERE model_code = 'deepseek-chat'
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT id
          FROM ai_model_config
          WHERE is_default = 1
      ) seeded_default
  );

UPDATE ai_model_config target_model
JOIN (
    SELECT id
    FROM ai_model_config
    WHERE enabled = 1
    ORDER BY priority_no ASC, id ASC
    LIMIT 1
) fallback_model ON fallback_model.id = target_model.id
SET target_model.is_default = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM (
        SELECT id
        FROM ai_model_config
        WHERE is_default = 1
    ) seeded_default
);

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_prompt_template'
              AND index_name = 'uk_ai_prompt_template_code'
        ),
        'ALTER TABLE ai_prompt_template DROP INDEX uk_ai_prompt_template_code',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_prompt_template'
              AND index_name = 'uk_ai_prompt_template_code_version'
        ),
        'SELECT 1',
        'ALTER TABLE ai_prompt_template ADD UNIQUE KEY uk_ai_prompt_template_code_version (template_code, version_no)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_prompt_template'
              AND index_name = 'idx_ai_prompt_template_code_enabled'
        ),
        'SELECT 1',
        'ALTER TABLE ai_prompt_template ADD KEY idx_ai_prompt_template_code_enabled (template_code, enabled, version_no)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ai_message_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    feedback_type VARCHAR(16) NOT NULL,
    feedback_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_message_feedback_message_user (message_id, user_id),
    KEY idx_ai_message_feedback_user_created (user_id, created_at DESC),
    KEY idx_ai_message_feedback_type_created (feedback_type, created_at DESC),
    KEY idx_ai_message_feedback_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
