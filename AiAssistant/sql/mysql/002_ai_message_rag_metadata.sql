ALTER TABLE ai_chat_message
    ADD COLUMN confidence DOUBLE NULL AFTER provider_code;

ALTER TABLE ai_chat_message
    ADD COLUMN refused_reason VARCHAR(255) NULL AFTER confidence;

ALTER TABLE ai_chat_message
    ADD COLUMN citations_json TEXT NULL AFTER refused_reason;

ALTER TABLE ai_chat_message
    ADD COLUMN used_tools_json TEXT NULL AFTER citations_json;

ALTER TABLE ai_chat_message
    ADD COLUMN answer_source VARCHAR(64) NULL AFTER used_tools_json;
