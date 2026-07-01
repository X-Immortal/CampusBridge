USE campusbridge;
SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;

-- Merge replyable task/post conversations by owner and peer.
-- SYSTEM conversations are intentionally excluded and remain separated by related_type/related_id.
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS conversation_merge_map;
CREATE TEMPORARY TABLE conversation_merge_map (
  old_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  keep_id BIGINT UNSIGNED NOT NULL,
  KEY idx_conversation_merge_keep (keep_id)
) ENGINE=InnoDB;

INSERT INTO conversation_merge_map (old_id, keep_id)
SELECT
  duplicate_conversation.id,
  canonical.keep_id
FROM conversations duplicate_conversation
JOIN (
  SELECT
    owner_id,
    peer_user_id,
    MIN(id) AS keep_id
  FROM conversations
  WHERE replyable = 1
    AND peer_user_id IS NOT NULL
    AND type IN ('PRIVATE', 'TASK')
  GROUP BY owner_id, peer_user_id
) canonical
  ON canonical.owner_id = duplicate_conversation.owner_id
 AND canonical.peer_user_id = duplicate_conversation.peer_user_id
WHERE duplicate_conversation.replyable = 1
  AND duplicate_conversation.peer_user_id IS NOT NULL
  AND duplicate_conversation.type IN ('PRIVATE', 'TASK')
  AND duplicate_conversation.id <> canonical.keep_id;

-- Message IDs are preserved, so MESSAGE attachments that reference message IDs remain valid.
UPDATE messages message_record
JOIN conversation_merge_map merge_map
  ON merge_map.old_id = message_record.conversation_id
SET message_record.conversation_id = merge_map.keep_id;

DELETE duplicate_conversation
FROM conversations duplicate_conversation
JOIN conversation_merge_map merge_map
  ON merge_map.old_id = duplicate_conversation.id;

-- All user-to-user conversations now use one common PRIVATE type.
-- The retained related_type/related_id only records the context that first created the conversation;
-- it no longer participates in conversation identity.
UPDATE conversations
SET type = 'PRIVATE'
WHERE replyable = 1
  AND peer_user_id IS NOT NULL
  AND type IN ('PRIVATE', 'TASK');

UPDATE conversations conversation_record
LEFT JOIN (
  SELECT conversation_id, MAX(created_at) AS latest_message_at
  FROM messages
  GROUP BY conversation_id
) latest_message
  ON latest_message.conversation_id = conversation_record.id
SET conversation_record.last_message_at = COALESCE(
  latest_message.latest_message_at,
  conversation_record.last_message_at
)
WHERE conversation_record.type = 'PRIVATE'
  AND conversation_record.replyable = 1;

DROP TEMPORARY TABLE conversation_merge_map;
COMMIT;

-- MySQL permits multiple NULL values in a UNIQUE index. The generated column is NULL for
-- SYSTEM conversations, while each replyable PRIVATE conversation gets its peer user ID.
ALTER TABLE conversations
  ADD COLUMN private_peer_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (
      CASE
        WHEN type = 'PRIVATE' AND replyable = 1 THEN peer_user_id
        ELSE NULL
      END
    ) STORED
    AFTER peer_user_id,
  ADD UNIQUE KEY uk_conversations_private_peer (owner_id, private_peer_id);

-- This query must return no rows after migration.
SELECT owner_id, peer_user_id, COUNT(*) AS conversation_count
FROM conversations
WHERE type = 'PRIVATE'
  AND replyable = 1
  AND peer_user_id IS NOT NULL
GROUP BY owner_id, peer_user_id
HAVING COUNT(*) > 1;
