-- Existing CampusBridge databases: allow the VERIFICATION owner type used by the API.
-- Select the target database before running this migration.
ALTER TABLE attachments
  MODIFY COLUMN owner_type
  ENUM('POST','REPLY','TASK','COMPLETION_PROOF','MESSAGE','REPORT','VERIFICATION') NULL;
