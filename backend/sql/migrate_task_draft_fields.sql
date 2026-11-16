-- Allow incomplete drafts; task publication validates these fields in TaskServiceImpl.
ALTER TABLE tasks
  MODIFY COLUMN `category` ENUM('ERRAND','AGENCY') NULL,
  MODIFY COLUMN `title` VARCHAR(100) NULL,
  MODIFY COLUMN `description` TEXT NULL,
  MODIFY COLUMN `campus` VARCHAR(50) NULL,
  MODIFY COLUMN `location` VARCHAR(120) NULL,
  MODIFY COLUMN `deadline` DATETIME NULL,
  MODIFY COLUMN `reward_type` ENUM('FREE','POINTS','CASH','NEGOTIABLE') NULL,
  MODIFY COLUMN `completion_standard` VARCHAR(500) NULL;
