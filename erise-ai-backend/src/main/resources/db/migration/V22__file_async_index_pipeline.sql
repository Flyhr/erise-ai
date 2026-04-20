CREATE TABLE IF NOT EXISTS ea_file_parse_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  chunk_payload_json LONGTEXT NOT NULL,
  plain_text LONGTEXT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_file_parse_result_file (file_id),
  KEY idx_ea_file_parse_result_owner_project (owner_user_id, project_id)
);
