ALTER TABLE ea_file
    ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED' AFTER preview_status,
    ADD COLUMN review_comment VARCHAR(500) NULL AFTER review_status,
    ADD COLUMN reviewed_by_user_id BIGINT NULL AFTER review_comment,
    ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by_user_id;

UPDATE ea_file
SET review_status = 'APPROVED'
WHERE review_status IS NULL OR review_status = '';

CREATE TABLE IF NOT EXISTS ea_user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    related_resource_type VARCHAR(64) NULL,
    related_resource_id BIGINT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_ea_user_notification_user_created (user_id, created_at DESC),
    KEY idx_ea_user_notification_read_created (user_id, read_flag, created_at DESC)
);
