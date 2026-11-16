-- Apply once to an existing database that does not have system_related_key.
-- Select the intended database explicitly before running this script.
-- Back up the database and stop application writes for the entire migration, including ALTER TABLE.
-- Messages keep their IDs, read_at and created_at values; MESSAGE attachment references remain valid.
-- PRIVATE conversations and SYSTEM conversations without a related object are unchanged.
START TRANSACTION;

CREATE TEMPORARY TABLE system_notification_merge_map (
  old_id BIGINT UNSIGNED PRIMARY KEY,
  keep_id BIGINT UNSIGNED NOT NULL,
  last_message_at DATETIME NULL,
  KEY idx_system_notification_keep (keep_id)
) ENGINE=InnoDB;

INSERT INTO system_notification_merge_map (old_id, keep_id, last_message_at)
SELECT c.id, canonical.keep_id, c.last_message_at
FROM conversations c
JOIN (
  SELECT owner_id, related_type, related_id, MIN(id) AS keep_id
  FROM conversations
  WHERE type = 'SYSTEM' AND related_type IS NOT NULL AND related_id IS NOT NULL
  GROUP BY owner_id, related_type, related_id
) canonical
  ON canonical.owner_id = c.owner_id
 AND canonical.related_type = c.related_type
 AND canonical.related_id = c.related_id
WHERE c.type = 'SYSTEM';

UPDATE conversations c
JOIN (
  SELECT keep_id, MAX(last_message_at) AS latest_at
  FROM system_notification_merge_map
  GROUP BY keep_id
) merged ON merged.keep_id = c.id
SET c.last_message_at = COALESCE(
  GREATEST(c.last_message_at, merged.latest_at), c.last_message_at, merged.latest_at
);

UPDATE messages m
JOIN system_notification_merge_map mapping ON mapping.old_id = m.conversation_id
SET m.conversation_id = mapping.keep_id
WHERE mapping.old_id <> mapping.keep_id;

DELETE c
FROM conversations c
JOIN system_notification_merge_map mapping ON mapping.old_id = c.id
WHERE mapping.old_id <> mapping.keep_id;

UPDATE conversations c
JOIN (
  SELECT conversation_id, MAX(created_at) AS latest_at FROM messages GROUP BY conversation_id
) latest_message ON latest_message.conversation_id = c.id
SET c.last_message_at = COALESCE(
  GREATEST(c.last_message_at, latest_message.latest_at), c.last_message_at, latest_message.latest_at
)
WHERE c.type = 'SYSTEM';

DROP TEMPORARY TABLE system_notification_merge_map;
COMMIT;

ALTER TABLE conversations
  ADD COLUMN system_related_key VARCHAR(80)
    GENERATED ALWAYS AS (
      CASE WHEN type = 'SYSTEM' THEN CONCAT(related_type, ':', related_id) ELSE NULL END
    ) STORED,
  ADD UNIQUE KEY uk_conversations_system_related (owner_id, system_related_key);

-- Expected result: no duplicate object-specific system conversations.
SELECT owner_id, related_type, related_id, COUNT(*) AS conversation_count
FROM conversations
WHERE type = 'SYSTEM' AND related_type IS NOT NULL AND related_id IS NOT NULL
GROUP BY owner_id, related_type, related_id
HAVING COUNT(*) > 1;
