ALTER TABLE ea_user_notification
    ADD COLUMN broadcast_flag TINYINT NOT NULL DEFAULT 0 AFTER read_flag;

UPDATE ea_user_notification n
SET n.broadcast_flag = 1
WHERE n.deleted = 0
  AND n.notification_type = 'ADMIN_NOTICE'
  AND EXISTS (
    SELECT 1
    FROM ea_audit_log al
    WHERE al.deleted = 0
      AND al.action_code = 'ADMIN_NOTIFICATION_SEND'
      AND al.operator_user_id = n.created_by
      AND JSON_VALID(al.detail_json)
      AND JSON_UNQUOTE(JSON_EXTRACT(al.detail_json, '$.title')) = n.title
      AND JSON_EXTRACT(al.detail_json, '$.sendToAll') = TRUE
      AND al.created_at BETWEEN DATE_SUB(n.created_at, INTERVAL 5 MINUTE)
                            AND DATE_ADD(n.created_at, INTERVAL 5 MINUTE)
  );

UPDATE ea_user_notification n
JOIN (
    SELECT DISTINCT created_by, title, content
    FROM ea_user_notification
    WHERE deleted = 0
      AND notification_type = 'ADMIN_NOTICE'
      AND broadcast_flag = 1
) flagged
  ON flagged.created_by = n.created_by
 AND flagged.title = n.title
 AND flagged.content = n.content
SET n.broadcast_flag = 1
WHERE n.deleted = 0
  AND n.notification_type = 'ADMIN_NOTICE';
