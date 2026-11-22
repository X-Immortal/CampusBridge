-- Read-only quality audit. Run after the demo schema and AI initializer.
-- Compatible with MySQL and H2 in MySQL mode. Expected issue_count: 0 per row.
SELECT 'task_required_fields' AS rule_id, COUNT(*) AS issue_count
FROM tasks
WHERE requester_id IS NULL OR title IS NULL OR TRIM(title) = ''
   OR deadline IS NULL OR completion_standard IS NULL OR TRIM(completion_standard) = ''
UNION ALL
SELECT 'task_enum', COUNT(*)
FROM tasks
WHERE category IS NULL OR category NOT IN ('ERRAND', 'AGENCY')
   OR reward_type IS NULL OR reward_type NOT IN ('FREE', 'POINTS', 'CASH', 'NEGOTIABLE')
   OR status IS NULL OR status NOT IN (
       'DRAFT', 'OPEN', 'PENDING_REVIEW', 'ACCEPTED', 'IN_PROGRESS',
       'PENDING_CONFIRMATION', 'COMPLETED', 'REVIEWED', 'CANCELLED', 'DISPUTED', 'REJECTED'
   )
UNION ALL
SELECT 'task_user_fk', COUNT(*)
FROM tasks t
LEFT JOIN users requester ON requester.id = t.requester_id
LEFT JOIN users provider ON provider.id = t.provider_id
WHERE requester.id IS NULL OR (t.provider_id IS NOT NULL AND provider.id IS NULL)
UNION ALL
SELECT 'history_fk', COUNT(*)
FROM task_histories h
LEFT JOIN tasks t ON t.id = h.task_id
LEFT JOIN users operator_user ON operator_user.id = h.operator_id
WHERE t.id IS NULL OR (h.operator_id IS NOT NULL AND operator_user.id IS NULL)
UNION ALL
SELECT 'verification_fk', COUNT(*)
FROM campus_verifications v
LEFT JOIN users applicant ON applicant.id = v.user_id
LEFT JOIN users reviewer ON reviewer.id = v.reviewer_id
WHERE applicant.id IS NULL OR (v.reviewer_id IS NOT NULL AND reviewer.id IS NULL)
UNION ALL
SELECT 'event_trace', COUNT(*)
FROM ai_event
WHERE trace_id IS NULL OR TRIM(trace_id) = ''
UNION ALL
SELECT 'event_session', COUNT(*)
FROM ai_event
WHERE session_id IS NULL OR TRIM(session_id) = '';
