ALTER TABLE ea_file
    ADD COLUMN archived TINYINT NOT NULL DEFAULT 0 AFTER index_status;

CREATE TABLE IF NOT EXISTS ai_action_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL,
  session_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  org_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  action_code VARCHAR(64) NOT NULL,
  match_rule VARCHAR(128) NOT NULL,
  permission_rule VARCHAR(128) NOT NULL,
  action_status VARCHAR(32) NOT NULL,
  target_type VARCHAR(32) NULL,
  target_id BIGINT NULL,
  model_code VARCHAR(128) NULL,
  provider_code VARCHAR(64) NULL,
  params_json LONGTEXT NULL,
  result_payload_json LONGTEXT NULL,
  fallback_message VARCHAR(500) NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  latency_ms INT NULL,
  success_flag TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_action_log_request (request_id),
  KEY idx_ai_action_log_session (session_id),
  KEY idx_ai_action_log_action_status (action_code, action_status),
  KEY idx_ai_action_log_user_created (user_id, created_at DESC)
);
