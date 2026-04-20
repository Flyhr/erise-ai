SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_name'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_name VARCHAR(128) NULL AFTER workflow_status"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_version'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_version VARCHAR(32) NULL AFTER workflow_name"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_domain'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_domain VARCHAR(64) NULL AFTER workflow_version"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_owner'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_owner VARCHAR(128) NULL AFTER workflow_domain"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'external_execution_id'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN external_execution_id VARCHAR(128) NULL AFTER workflow_owner"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_error_summary'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_error_summary VARCHAR(500) NULL AFTER external_execution_id"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'workflow_duration_ms'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_duration_ms INT NULL AFTER workflow_error_summary"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'delivery_retryable'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN delivery_retryable TINYINT NOT NULL DEFAULT 0 AFTER workflow_duration_ms"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'manual_status'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN manual_status VARCHAR(32) NULL AFTER delivery_retryable"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'manual_reason'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN manual_reason VARCHAR(500) NULL AFTER manual_status"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'manual_replay_count'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN manual_replay_count INT NOT NULL DEFAULT 0 AFTER manual_reason"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'replayed_from_event_id'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN replayed_from_event_id BIGINT NULL AFTER manual_replay_count"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'last_callback_at'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN last_callback_at DATETIME NULL AFTER replayed_from_event_id"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'callback_payload_json'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN callback_payload_json LONGTEXT NULL AFTER payload_json"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE n8n_event_log
SET workflow_status = CASE
  WHEN workflow_status = 'WAITING_FOR_REVIEW' THEN 'PENDING'
  WHEN workflow_status = 'PROCESSING' THEN 'RUNNING'
  WHEN workflow_status = 'REJECTED' THEN 'CANCELLED'
  WHEN workflow_status IS NULL OR workflow_status = '' THEN
    CASE
      WHEN delivery_status IN ('FAILED', 'SKIPPED') THEN 'NOT_STARTED'
      ELSE 'PENDING'
    END
  ELSE workflow_status
END;

UPDATE n8n_event_log
SET workflow_name = COALESCE(NULLIF(workflow_name, ''), NULLIF(workflow_hint, ''), event_type)
WHERE workflow_name IS NULL OR workflow_name = '';

UPDATE n8n_event_log
SET workflow_version = COALESCE(NULLIF(workflow_version, ''), '2026.04.1')
WHERE workflow_version IS NULL OR workflow_version = '';

UPDATE n8n_event_log
SET workflow_domain = CASE
  WHEN workflow_domain IS NOT NULL AND workflow_domain <> '' THEN workflow_domain
  WHEN workflow_hint LIKE 'approval-%' THEN 'approval'
  WHEN workflow_hint LIKE 'notification-%' THEN 'notification'
  WHEN workflow_hint LIKE 'health-%' THEN 'operations'
  WHEN workflow_hint LIKE 'weekly-%' THEN 'reporting'
  ELSE 'operations'
END;

UPDATE n8n_event_log
SET workflow_owner = CASE
  WHEN workflow_owner IS NOT NULL AND workflow_owner <> '' THEN workflow_owner
  WHEN workflow_hint LIKE 'weekly-%' THEN 'knowledge-ops'
  ELSE 'platform-ops'
END;

UPDATE n8n_event_log
SET delivery_retryable = CASE
  WHEN error_code IN ('N8N_TIMEOUT', 'N8N_NETWORK_ERROR', 'N8N_RATE_LIMITED', 'N8N_UPSTREAM_UNAVAILABLE') THEN 1
  ELSE delivery_retryable
END
WHERE delivery_status = 'FAILED';

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_workflow_name'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_workflow_name (workflow_name, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_external_execution'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_external_execution (external_execution_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_manual_status'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_manual_status (manual_status, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_replayed_from'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_replayed_from (replayed_from_event_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_last_callback'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_last_callback (last_callback_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
