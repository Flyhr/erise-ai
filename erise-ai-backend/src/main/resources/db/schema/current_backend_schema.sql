-- Consolidated Erise backend schema for fresh database bootstrap/reference.
-- Flyway migrations in db/migration remain the canonical upgrade path.
-- Do not place this file under db/migration unless intentionally resetting
-- the Flyway baseline for a new environment.

CREATE TABLE IF NOT EXISTS ea_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(128),
  password_hash VARCHAR(255) NOT NULL,
  role_code VARCHAR(32) NOT NULL DEFAULT 'USER',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_user_username (username),
  UNIQUE KEY uk_ea_user_email (email),
  KEY idx_ea_user_role_status (role_code, status),
  KEY idx_ea_user_deleted_created (deleted, created_at)
);

CREATE TABLE IF NOT EXISTS ea_user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  avatar_url LONGTEXT,
  bio VARCHAR(500),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_user_profile_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS ea_user_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(64) NOT NULL,
  login_ip VARCHAR(64),
  user_agent VARCHAR(255),
  success TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_user_login_log_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS ea_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500),
  project_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  archived TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_project_owner_created (owner_user_id, created_at DESC),
  KEY idx_ea_project_owner_status (owner_user_id, project_status)
);

CREATE TABLE IF NOT EXISTS ea_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(32),
  mime_type VARCHAR(128),
  file_size BIGINT NOT NULL DEFAULT 0,
  storage_provider VARCHAR(32) NOT NULL DEFAULT 'MINIO',
  storage_bucket VARCHAR(128) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  checksum_md5 VARCHAR(64),
  checksum_sha256 VARCHAR(64),
  upload_status VARCHAR(32) NOT NULL DEFAULT 'INIT',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  preview_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  review_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
  review_comment VARCHAR(500) NULL,
  reviewed_by_user_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  archived TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  FULLTEXT KEY ft_ea_file_name (file_name),
  KEY idx_ea_file_project_deleted_created (project_id, deleted, created_at DESC),
  KEY idx_ea_file_owner_status (owner_user_id, upload_status, parse_status)
);

CREATE TABLE IF NOT EXISTS ea_file_parse_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_file_parse_task_status_created (task_status, created_at),
  KEY idx_ea_file_parse_task_file (file_id)
);

CREATE TABLE IF NOT EXISTS ea_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1000),
  doc_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  latest_version_no INT NOT NULL DEFAULT 0,
  editor_type VARCHAR(32) NOT NULL DEFAULT 'TIPTAP',
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_document_project_status_updated (project_id, doc_status, updated_at DESC),
  KEY idx_ea_document_owner_created (owner_user_id, created_at DESC)
);

CREATE TABLE IF NOT EXISTS ea_document_content (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  content_json LONGTEXT,
  content_html_snapshot LONGTEXT,
  plain_text LONGTEXT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_document_content_document (document_id),
  FULLTEXT KEY ft_ea_document_content_plain_text (plain_text(1000))
);

CREATE TABLE IF NOT EXISTS ea_document_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  content_json LONGTEXT,
  content_html_snapshot LONGTEXT,
  plain_text LONGTEXT,
  published_by BIGINT NOT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_document_version_doc_version (document_id, version_no)
);

CREATE TABLE IF NOT EXISTS ea_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(32),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_tag_owner_name (owner_user_id, name)
);

CREATE TABLE IF NOT EXISTS ea_file_tag_rel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_file_tag_rel (file_id, tag_id)
);

CREATE TABLE IF NOT EXISTS ea_document_tag_rel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_document_tag_rel (document_id, tag_id)
);

CREATE TABLE IF NOT EXISTS ea_search_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  keyword VARCHAR(255) NOT NULL,
  project_id BIGINT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_search_history_user_created (user_id, created_at DESC)
);

CREATE TABLE IF NOT EXISTS ea_knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  source_title VARCHAR(255) NOT NULL,
  chunk_index INT NOT NULL DEFAULT 0,
  chunk_text LONGTEXT NOT NULL,
  page_no INT,
  section_path VARCHAR(255),
  embedding_ref VARCHAR(255),
  index_status VARCHAR(32) NOT NULL DEFAULT 'READY',
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  FULLTEXT KEY ft_ea_knowledge_chunk_text (chunk_text(1000)),
  KEY idx_ea_knowledge_chunk_project_source (project_id, source_type, source_id),
  KEY idx_ea_knowledge_chunk_owner_project (owner_user_id, project_id)
);

CREATE TABLE IF NOT EXISTS ea_ai_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  last_message_at DATETIME,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_ai_session_owner_last_message (owner_user_id, last_message_at DESC)
);

CREATE TABLE IF NOT EXISTS ea_ai_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  content LONGTEXT NOT NULL,
  confidence DECIMAL(5,2),
  refused_reason VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_ai_message_session_created (session_id, created_at)
);

CREATE TABLE IF NOT EXISTS ea_ai_citation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  source_title VARCHAR(255) NOT NULL,
  snippet VARCHAR(1000),
  page_no INT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_ai_citation_message (message_id)
);

CREATE TABLE IF NOT EXISTS ea_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT,
  task_type VARCHAR(64) NOT NULL,
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  payload_json LONGTEXT,
  result_json LONGTEXT,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_task_type_status_created (task_type, task_status, created_at DESC)
);

CREATE TABLE IF NOT EXISTS ea_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_user_id BIGINT,
  operator_username VARCHAR(64),
  action_code VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64),
  resource_id BIGINT,
  detail_json LONGTEXT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_audit_log_operator_created (operator_user_id, created_at DESC),
  KEY idx_ea_audit_log_action_created (action_code, created_at DESC)
);

CREATE TABLE IF NOT EXISTS ea_ai_model_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_name VARCHAR(128) NOT NULL,
  provider_code VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  is_default TINYINT NOT NULL DEFAULT 0,
  config_json LONGTEXT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_ai_model_config_name (model_name)
);

INSERT INTO ea_ai_model_config (model_name, provider_code, enabled, is_default, config_json, created_by, updated_by)
SELECT 'default-chat', 'OPENAI_COMPAT', 1, 1, JSON_OBJECT('mode', 'chat'), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM ea_ai_model_config WHERE model_name = 'default-chat');

CREATE TABLE IF NOT EXISTS ea_content_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1000),
  content_json LONGTEXT,
  plain_text LONGTEXT,
  cover_meta_json LONGTEXT,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_content_item_project_type_updated (project_id, item_type, updated_at DESC),
  KEY idx_ea_content_item_owner_project (owner_user_id, project_id)
);

CREATE TABLE IF NOT EXISTS ea_file_edit_content (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  content_html_snapshot LONGTEXT,
  plain_text LONGTEXT,
  editor_type VARCHAR(32) NOT NULL DEFAULT 'OFFICE_HTML',
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_file_edit_content_file (file_id)
);

CREATE TABLE IF NOT EXISTS ea_ai_user_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  general_web_search_enabled TINYINT NOT NULL DEFAULT 0,
  knowledge_similarity_threshold DECIMAL(5,4) NOT NULL DEFAULT 0.7500,
  knowledge_top_k INT NOT NULL DEFAULT 5,
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
  project_id BIGINT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(32) NOT NULL,
  mime_type VARCHAR(128),
  file_size BIGINT NOT NULL DEFAULT 0,
  storage_bucket VARCHAR(128) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  plain_text LONGTEXT,
  parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  last_error VARCHAR(1000) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_ai_temp_file_session_created (session_id, created_at),
  KEY idx_ea_ai_temp_file_owner_session (owner_user_id, session_id)
);

CREATE TABLE IF NOT EXISTS ea_rag_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  session_id BIGINT NOT NULL DEFAULT 0,
  scope_type VARCHAR(16) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  source_title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  content_hash VARCHAR(64) NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  last_indexed_at DATETIME NULL,
  last_error VARCHAR(1000) NULL,
  embedding_model_code VARCHAR(128) NULL,
  embedding_version VARCHAR(64) NULL,
  embedding_dimension INT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_rag_source_scope (owner_user_id, scope_type, source_type, source_id, session_id),
  KEY idx_ea_rag_source_owner_project (owner_user_id, project_id, status),
  KEY idx_ea_rag_source_owner_session (owner_user_id, session_id, status)
);

CREATE TABLE IF NOT EXISTS ea_rag_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rag_source_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  session_id BIGINT NOT NULL DEFAULT 0,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  chunk_num INT NOT NULL DEFAULT 0,
  chunk_text LONGTEXT NOT NULL,
  page_no INT NULL,
  section_path VARCHAR(255) NULL,
  vector_collection VARCHAR(64) NOT NULL,
  vector_point_id VARCHAR(64) NOT NULL,
  embedding_model_code VARCHAR(128) NULL,
  embedding_version VARCHAR(64) NULL,
  embedding_dimension INT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_rag_chunk_source_chunk (rag_source_id, chunk_num),
  KEY idx_ea_rag_chunk_owner_project (owner_user_id, project_id, source_type, source_id),
  KEY idx_ea_rag_chunk_session_source (owner_user_id, session_id, source_type, source_id),
  FULLTEXT KEY ft_ea_rag_chunk_text (chunk_text(1000))
);

CREATE TABLE IF NOT EXISTS ea_rag_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  session_id BIGINT NOT NULL DEFAULT 0,
  rag_source_id BIGINT NULL,
  task_type VARCHAR(32) NOT NULL DEFAULT 'INDEX',
  task_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  scope_type VARCHAR(16) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  payload_json LONGTEXT NULL,
  result_json LONGTEXT NULL,
  last_error VARCHAR(1000) NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_rag_task_owner_status (owner_user_id, task_status, created_at),
  KEY idx_ea_rag_task_source (scope_type, source_type, source_id, session_id)
);

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  org_id BIGINT NOT NULL DEFAULT 0,
  project_id BIGINT NULL,
  scene VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary_text TEXT NULL,
  last_message_at DATETIME(6) NULL,
  message_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_ai_chat_session_user_id (user_id),
  KEY idx_ai_chat_session_project_id (project_id),
  KEY idx_ai_chat_session_last_message_at (last_message_at),
  KEY idx_ai_chat_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_chat_message (
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
  confidence DOUBLE NULL,
  refused_reason VARCHAR(255) NULL,
  citations_json TEXT NULL,
  used_tools_json TEXT NULL,
  answer_source VARCHAR(64) NULL,
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
  PRIMARY KEY (id),
  KEY idx_ai_chat_message_session_id (session_id),
  KEY idx_ai_chat_message_user_id (user_id),
  KEY idx_ai_chat_message_status (message_status),
  KEY idx_ai_chat_message_request_id (request_id),
  KEY idx_ai_chat_message_session_sequence (session_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  org_id BIGINT NOT NULL DEFAULT 0,
  project_id BIGINT NULL,
  user_message_id BIGINT NULL,
  assistant_message_id BIGINT NULL,
  provider_code VARCHAR(64) NOT NULL,
  model_code VARCHAR(128) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  temperature DOUBLE NULL,
  max_tokens INT NULL,
  stream BOOLEAN NOT NULL DEFAULT FALSE,
  request_payload_json TEXT NULL,
  response_payload_json TEXT NULL,
  answer_source VARCHAR(64) NULL,
  message_status VARCHAR(32) NULL,
  input_token_count INT NULL,
  output_token_count INT NULL,
  total_token_count INT NULL,
  latency_ms INT NULL,
  duration_ms INT NULL,
  success_flag BOOLEAN NOT NULL DEFAULT FALSE,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_ai_request_log_request_id (request_id),
  KEY idx_ai_request_log_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  template_code VARCHAR(128) NOT NULL,
  template_name VARCHAR(255) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  system_prompt TEXT NOT NULL,
  user_prompt_wrapper TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version_no INT NOT NULL DEFAULT 1,
  created_by VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_prompt_template_code_version (template_code, version_no),
  KEY idx_ai_prompt_template_scene (scene),
  KEY idx_ai_prompt_template_code_enabled (template_code, enabled, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_model_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider_code VARCHAR(64) NOT NULL,
  model_code VARCHAR(128) NOT NULL,
  model_name VARCHAR(255) NOT NULL,
  base_url VARCHAR(255) NULL,
  api_key_ref VARCHAR(64) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  support_stream BOOLEAN NOT NULL DEFAULT TRUE,
  support_system_prompt BOOLEAN NOT NULL DEFAULT TRUE,
  max_context_tokens INT NULL,
  input_price_per_million DECIMAL(12,4) NULL,
  output_price_per_million DECIMAL(12,4) NULL,
  currency_code VARCHAR(16) NOT NULL DEFAULT 'USD',
  priority_no INT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_model_config_model_code (model_code),
  KEY idx_ai_model_config_provider_priority (provider_code, priority_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_message_citation (
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
  section_path VARCHAR(255) NULL,
  score DOUBLE NULL,
  url VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_ai_message_citation_message_id (message_id),
  KEY idx_ai_message_citation_session_id (session_id),
  KEY idx_ai_message_citation_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ea_rag_sparse_term (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rag_source_id BIGINT NOT NULL,
  rag_chunk_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  session_id BIGINT NOT NULL DEFAULT 0,
  scope_type VARCHAR(16) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  term VARCHAR(128) NOT NULL,
  field_code VARCHAR(16) NOT NULL,
  term_freq INT NOT NULL DEFAULT 1,
  doc_len INT NOT NULL DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ea_rag_sparse_term_chunk_field_term (rag_chunk_id, field_code, term),
  KEY idx_ea_rag_sparse_term_source (rag_source_id, rag_chunk_id),
  KEY idx_ea_rag_sparse_term_lookup (scope_type, owner_user_id, term, source_type, project_id, session_id),
  KEY idx_ea_rag_sparse_term_source_ref (source_type, source_id, session_id)
);

CREATE TABLE IF NOT EXISTS ea_user_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  notification_type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  related_resource_type VARCHAR(64) NULL,
  related_resource_id BIGINT NULL,
  read_flag TINYINT NOT NULL DEFAULT 0,
  broadcast_flag TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ea_user_notification_user_created (user_id, created_at DESC),
  KEY idx_ea_user_notification_read_created (user_id, read_flag, created_at DESC)
);

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
