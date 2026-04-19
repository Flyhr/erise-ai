CREATE TABLE IF NOT EXISTS n8n_event_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  workflow_hint VARCHAR(128) NULL,
  approval_id BIGINT NULL,
  session_id BIGINT NULL,
  user_id BIGINT NULL,
  project_id BIGINT NULL,
  target_url VARCHAR(1000) NULL,
  status_code INT NULL,
  success_flag TINYINT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  payload_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_n8n_event_request (request_id),
  KEY idx_n8n_event_type (event_type),
  KEY idx_n8n_event_workflow (workflow_hint)
);

CREATE TABLE IF NOT EXISTS automation_webhook_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL,
  workflow_code VARCHAR(128) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  status_code INT NULL,
  success_flag TINYINT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  request_payload_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_automation_webhook_request (request_id),
  KEY idx_automation_webhook_workflow (workflow_code),
  KEY idx_automation_webhook_event (event_type)
);
