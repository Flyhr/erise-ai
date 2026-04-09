ALTER TABLE ea_ai_temp_file
    ADD COLUMN last_error VARCHAR(1000) NULL AFTER index_status,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER last_error;
