SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND column_name = 'delivery_status'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER target_url"
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
      AND column_name = 'workflow_status'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN workflow_status VARCHAR(64) NULL AFTER delivery_status"
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
      AND column_name = 'error_code'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN error_code VARCHAR(64) NULL AFTER success_flag"
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
      AND column_name = 'attempt_count'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER error_message"
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
      AND column_name = 'max_attempts'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 AFTER attempt_count"
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
      AND column_name = 'idempotency_key'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER max_attempts"
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
      AND column_name = 'signature'
  ),
  'SELECT 1',
  "ALTER TABLE n8n_event_log ADD COLUMN signature VARCHAR(128) NULL AFTER idempotency_key"
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE n8n_event_log
SET delivery_status = CASE
  WHEN success_flag = 1 THEN 'DELIVERED'
  WHEN target_url IS NULL OR target_url = '' THEN 'SKIPPED'
  ELSE 'FAILED'
END
WHERE delivery_status IS NULL OR delivery_status = '';

UPDATE n8n_event_log
SET attempt_count = CASE
  WHEN attempt_count < 1 AND (status_code IS NOT NULL OR error_message IS NOT NULL OR success_flag = 1) THEN 1
  ELSE attempt_count
END;

UPDATE n8n_event_log
SET max_attempts = CASE
  WHEN max_attempts < 1 THEN 1
  ELSE max_attempts
END;

SET @ddl := IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'n8n_event_log'
      AND index_name = 'idx_n8n_event_delivery_status'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_delivery_status (delivery_status, created_at)'
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
      AND index_name = 'idx_n8n_event_workflow_status'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_workflow_status (workflow_status, created_at)'
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
      AND index_name = 'idx_n8n_event_idempotency_key'
  ),
  'SELECT 1',
  'ALTER TABLE n8n_event_log ADD KEY idx_n8n_event_idempotency_key (idempotency_key)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
