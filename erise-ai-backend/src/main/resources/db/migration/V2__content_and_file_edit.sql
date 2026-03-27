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