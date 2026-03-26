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
  avatar_url VARCHAR(255),
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
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
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
  project_id BIGINT NOT NULL,
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
