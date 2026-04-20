ALTER TABLE ea_file_parse_task
  ADD COLUMN queue_lane VARCHAR(16) NOT NULL DEFAULT 'NORMAL' AFTER task_status;

CREATE INDEX idx_ea_file_parse_task_lane_status_created
  ON ea_file_parse_task (queue_lane, task_status, created_at);

CREATE INDEX idx_ea_file_owner_checksum
  ON ea_file (owner_user_id, checksum_md5, updated_at);
