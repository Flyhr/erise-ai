ALTER TABLE ea_ai_user_setting
    ADD COLUMN knowledge_top_k INT NOT NULL DEFAULT 5 AFTER knowledge_similarity_threshold;

ALTER TABLE ea_ai_temp_file
    ADD COLUMN project_id BIGINT NULL AFTER owner_user_id,
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER parse_status;

UPDATE ea_ai_temp_file
SET parse_status = CASE
                       WHEN parse_status = 'SUCCESS' THEN 'INDEXED'
                       WHEN parse_status = 'FAILED' THEN 'FAILED'
                       ELSE parse_status
    END,
    index_status = CASE
                       WHEN parse_status = 'SUCCESS' THEN 'INDEXED'
                       WHEN parse_status = 'FAILED' THEN 'FAILED'
                       ELSE 'PENDING'
        END;
