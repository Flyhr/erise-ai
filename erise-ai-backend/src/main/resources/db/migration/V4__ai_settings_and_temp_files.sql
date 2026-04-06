CREATE TABLE IF NOT EXISTS ea_ai_user_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  general_web_search_enabled TINYINT NOT NULL DEFAULT 0,
  knowledge_similarity_threshold DECIMAL(5,4) NOT NULL DEFAULT 0.7500,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_ai_user_setting_user (user_id)
);

CREATE TABLE IF NOT EXISTS ea_ai_temp_file (
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
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_ai_temp_file_session_created (session_id, created_at),
  KEY idx_ea_ai_temp_file_owner_session (owner_user_id, session_id)
);
