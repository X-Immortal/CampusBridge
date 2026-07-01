-- 校园互助服务平台 database schema.
-- MySQL 8.0 / InnoDB / utf8mb4
USE campusbridge;
CREATE TABLE `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名/学号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `phone_cipher` VARCHAR(255) NULL COMMENT '手机号密文',
  `phone_hash` CHAR(64) NULL COMMENT '手机号哈希，用于查重',
  `email` VARCHAR(120) NULL COMMENT '邮箱',
  `role` ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT '角色',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_phone_hash` (`phone_hash`),
  KEY `idx_users_role_enabled` (`role`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

CREATE TABLE `user_profiles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `nickname` VARCHAR(50) NULL,
  `bio` VARCHAR(500) NULL,
  `campus` VARCHAR(100) NULL,
  `college` VARCHAR(100) NULL,
  `contact_info` VARCHAR(255) NULL COMMENT '用户主动公开的联系方式',
  `avatar_url` VARCHAR(500) NULL,
  `interest_tags` JSON NULL,
  `skill_tags` JSON NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_profiles_user_id` (`user_id`),
  KEY `idx_user_profiles_campus` (`campus`),
  CONSTRAINT `fk_user_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户公开资料';

CREATE TABLE `campus_verifications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `method` ENUM('STUDENT_CARD_IMAGE','CAMPUS_EMAIL','MANUAL_REVIEW') NOT NULL,
  `real_name_cipher` VARCHAR(255) NOT NULL COMMENT '真实姓名密文',
  `student_number_cipher` VARCHAR(255) NOT NULL COMMENT '学号密文',
  `student_number_hash` CHAR(64) NULL COMMENT '学号哈希，用于查重',
  `school` VARCHAR(100) NOT NULL,
  `department` VARCHAR(100) NULL,
  `evidence_url` VARCHAR(500) NULL,
  `status` ENUM('UNVERIFIED','PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `reviewer_id` BIGINT UNSIGNED NULL,
  `reject_reason` VARCHAR(300) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_campus_verifications_user_status` (`user_id`,`status`),
  KEY `idx_campus_verifications_status` (`status`),
  KEY `idx_campus_verifications_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_campus_verifications_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_campus_verifications_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园身份认证';

CREATE TABLE `user_credits` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `points` INT NOT NULL DEFAULT 0 COMMENT '当前积分',
  `level` INT NOT NULL DEFAULT 1 COMMENT '积分等级',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_credits_user_id` (`user_id`),
  KEY `idx_user_credits_level` (`level`),
  CONSTRAINT `fk_user_credits_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分等级';

CREATE TABLE `credit_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `change_value` INT NOT NULL COMMENT '积分变化值',
  `reason` VARCHAR(255) NOT NULL,
  `source_type` VARCHAR(50) NULL COMMENT '来源对象类型',
  `source_id` BIGINT UNSIGNED NULL COMMENT '来源对象ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_credit_records_user_created` (`user_id`,`created_at`),
  KEY `idx_credit_records_source` (`source_type`,`source_id`),
  CONSTRAINT `fk_credit_records_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变化记录';

CREATE TABLE `posts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `author_id` BIGINT UNSIGNED NOT NULL,
  `category` ENUM('INQUIRY_HELP','SECOND_HAND','TEAM_FRIEND','CAMPUS_SOCIAL') NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `content` TEXT NOT NULL,
  `status` ENUM('DRAFT','PUBLISHED','PENDING_REVIEW','HIDDEN','CLOSED','DELETED','REJECTED') NOT NULL DEFAULT 'PUBLISHED',
  `reply_count` INT NOT NULL DEFAULT 0,
  `like_count` INT NOT NULL DEFAULT 0,
  `mark_count` INT NOT NULL DEFAULT 0,
  `accepted_reply_id` BIGINT UNSIGNED NULL COMMENT '被采纳回复',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_posts_author` (`author_id`),
  KEY `idx_posts_category_status_created` (`category`,`status`,`created_at`),
  KEY `idx_posts_status_created` (`status`,`created_at`),
  KEY `idx_posts_accepted_reply` (`accepted_reply_id`),
  CONSTRAINT `fk_posts_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区型需求帖子';

CREATE TABLE `replies` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT UNSIGNED NOT NULL,
  `author_id` BIGINT UNSIGNED NOT NULL,
  `parent_reply_id` BIGINT UNSIGNED NULL,
  `content` TEXT NOT NULL,
  `status` ENUM('DRAFT','PUBLISHED','PENDING_REVIEW','HIDDEN','CLOSED','DELETED','REJECTED') NOT NULL DEFAULT 'PUBLISHED',
  `like_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_replies_post_created` (`post_id`,`created_at`),
  KEY `idx_replies_author` (`author_id`),
  KEY `idx_replies_parent` (`parent_reply_id`),
  CONSTRAINT `fk_replies_post` FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`),
  CONSTRAINT `fk_replies_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_replies_parent` FOREIGN KEY (`parent_reply_id`) REFERENCES `replies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子回复';

CREATE TABLE `post_interactions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `target_type` ENUM('POST','REPLY') NOT NULL,
  `target_id` BIGINT UNSIGNED NOT NULL,
  `type` ENUM('LIKE','MARK') NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_interactions_user_target_type` (`user_id`,`target_type`,`target_id`,`type`),
  KEY `idx_post_interactions_target` (`target_type`,`target_id`,`type`),
  CONSTRAINT `fk_post_interactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞与标记';

CREATE TABLE `tasks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT UNSIGNED NOT NULL,
  `provider_id` BIGINT UNSIGNED NULL,
  `category` ENUM('ERRAND','AGENCY') NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `description` TEXT NOT NULL,
  `campus` VARCHAR(50) NOT NULL,
  `location` VARCHAR(120) NOT NULL,
  `deadline` DATETIME NOT NULL,
  `reward_type` ENUM('FREE','POINTS','CASH','NEGOTIABLE') NOT NULL,
  `reward_description` VARCHAR(200) NULL,
  `reward_amount` INT NULL COMMENT '积分/金额奖励数值',
  `completion_standard` VARCHAR(500) NOT NULL,
  `requires_proof` TINYINT(1) NOT NULL DEFAULT 0,
  `only_in_app_contact` TINYINT(1) NOT NULL DEFAULT 1,
  `allow_offline_contact` TINYINT(1) NOT NULL DEFAULT 0,
  `is_instant_task` TINYINT(1) NOT NULL DEFAULT 0,
  `route_hint` VARCHAR(300) NULL,
  `risk_disclosure_confirmed` TINYINT(1) NOT NULL DEFAULT 0,
  `sensitive_reason` VARCHAR(300) NULL,
  `status` ENUM('DRAFT','OPEN','PENDING_REVIEW','ACCEPTED','IN_PROGRESS','PENDING_CONFIRMATION','COMPLETED','REVIEWED','CANCELLED','DISPUTED','REJECTED') NOT NULL DEFAULT 'OPEN',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tasks_requester` (`requester_id`),
  KEY `idx_tasks_provider` (`provider_id`),
  KEY `idx_tasks_category_status_created` (`category`,`status`,`created_at`),
  KEY `idx_tasks_status_deadline` (`status`,`deadline`),
  KEY `idx_tasks_campus_status_created` (`campus`,`status`,`created_at`),
  CONSTRAINT `fk_tasks_requester` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_tasks_provider` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务/订单型需求';

CREATE TABLE `task_applications` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `applicant_id` BIGINT UNSIGNED NOT NULL,
  `message` VARCHAR(300) NOT NULL,
  `status` ENUM('PENDING','ACCEPTED','REJECTED','WITHDRAWN') NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_applications_task_applicant` (`task_id`,`applicant_id`),
  KEY `idx_task_applications_task_status` (`task_id`,`status`),
  KEY `idx_task_applications_applicant` (`applicant_id`),
  CONSTRAINT `fk_task_applications_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`),
  CONSTRAINT `fk_task_applications_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务接单申请';

CREATE TABLE `completion_proofs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `submitter_id` BIGINT UNSIGNED NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_completion_proofs_task` (`task_id`),
  KEY `idx_completion_proofs_submitter` (`submitter_id`),
  CONSTRAINT `fk_completion_proofs_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`),
  CONSTRAINT `fk_completion_proofs_submitter` FOREIGN KEY (`submitter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务完成凭证';

CREATE TABLE `task_histories` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `from_status` ENUM('DRAFT','OPEN','PENDING_REVIEW','ACCEPTED','IN_PROGRESS','PENDING_CONFIRMATION','COMPLETED','REVIEWED','CANCELLED','DISPUTED','REJECTED') NULL,
  `to_status` ENUM('DRAFT','OPEN','PENDING_REVIEW','ACCEPTED','IN_PROGRESS','PENDING_CONFIRMATION','COMPLETED','REVIEWED','CANCELLED','DISPUTED','REJECTED') NOT NULL,
  `operator_id` BIGINT UNSIGNED NULL,
  `note` VARCHAR(300) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_histories_task_created` (`task_id`,`created_at`),
  KEY `idx_task_histories_operator` (`operator_id`),
  CONSTRAINT `fk_task_histories_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`),
  CONSTRAINT `fk_task_histories_operator` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务状态流转历史';

CREATE TABLE `reviews` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL,
  `reviewer_id` BIGINT UNSIGNED NOT NULL,
  `reviewee_id` BIGINT UNSIGNED NOT NULL,
  `rating` TINYINT NOT NULL COMMENT '评分1-5',
  `comment` VARCHAR(500) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reviews_task_reviewer_reviewee` (`task_id`,`reviewer_id`,`reviewee_id`),
  KEY `idx_reviews_task` (`task_id`),
  KEY `idx_reviews_reviewee_created` (`reviewee_id`,`created_at`),
  CONSTRAINT `fk_reviews_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`),
  CONSTRAINT `fk_reviews_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_reviews_reviewee` FOREIGN KEY (`reviewee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_reviews_rating` CHECK (`rating` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务双向评价';

CREATE TABLE `conversations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `type` ENUM('PRIVATE','SYSTEM','TASK') NOT NULL,
  `title` VARCHAR(100) NULL,
  `owner_id` BIGINT UNSIGNED NOT NULL COMMENT '当前用户视角下的会话拥有者',
  `peer_user_id` BIGINT UNSIGNED NULL,
  `private_peer_id` BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE
      WHEN `type` = 'PRIVATE' AND `replyable` = 1 THEN `peer_user_id`
      ELSE NULL
    END
  ) STORED,
  `related_type` ENUM('POST','TASK','SYSTEM') NULL,
  `related_id` BIGINT UNSIGNED NULL,
  `replyable` TINYINT(1) NOT NULL DEFAULT 1,
  `last_message_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conversations_owner_last` (`owner_id`,`last_message_at`),
  KEY `idx_conversations_peer` (`peer_user_id`),
  KEY `idx_conversations_related` (`related_type`,`related_id`),
  UNIQUE KEY `uk_conversations_private_peer` (`owner_id`,`private_peer_id`),
  CONSTRAINT `fk_conversations_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_conversations_peer` FOREIGN KEY (`peer_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内会话';

CREATE TABLE `messages` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `sender_id` BIGINT UNSIGNED NULL COMMENT '系统通知可为空',
  `type` ENUM('TEXT','IMAGE','SYSTEM_NOTIFICATION') NOT NULL DEFAULT 'TEXT',
  `content` TEXT NOT NULL,
  `read_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_messages_conversation_created` (`conversation_id`,`created_at`),
  KEY `idx_messages_sender` (`sender_id`),
  KEY `idx_messages_read` (`conversation_id`,`read_at`),
  CONSTRAINT `fk_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`),
  CONSTRAINT `fk_messages_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息';

CREATE TABLE `attachments` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uploader_id` BIGINT UNSIGNED NOT NULL,
  `owner_type` ENUM('POST','REPLY','TASK','COMPLETION_PROOF','MESSAGE','REPORT') NULL,
  `owner_id` BIGINT UNSIGNED NULL,
  `type` ENUM('IMAGE','FILE') NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `file_url` VARCHAR(500) NOT NULL,
  `content_type` VARCHAR(100) NOT NULL,
  `size` BIGINT UNSIGNED NOT NULL,
  `status` ENUM('TEMPORARY','BOUND','DELETED') NOT NULL DEFAULT 'TEMPORARY',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_attachments_owner` (`owner_type`,`owner_id`),
  KEY `idx_attachments_uploader_status` (`uploader_id`,`status`,`created_at`),
  CONSTRAINT `fk_attachments_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用附件';

CREATE TABLE `reports` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reporter_id` BIGINT UNSIGNED NOT NULL,
  `target_type` ENUM('POST','REPLY','TASK','USER','MESSAGE') NOT NULL,
  `target_id` BIGINT UNSIGNED NOT NULL,
  `reason` VARCHAR(100) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `status` ENUM('PENDING','PROCESSING','APPROVED','REJECTED','RESOLVED','PUNISHED') NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_reports_reporter_created` (`reporter_id`,`created_at`),
  KEY `idx_reports_target` (`target_type`,`target_id`),
  KEY `idx_reports_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_reports_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报与申诉';

CREATE TABLE `moderation_records` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `report_id` BIGINT UNSIGNED NULL,
  `target_type` ENUM('POST','REPLY','TASK','USER','MESSAGE') NOT NULL,
  `target_id` BIGINT UNSIGNED NOT NULL,
  `handler_id` BIGINT UNSIGNED NOT NULL,
  `status` ENUM('PENDING','PROCESSING','APPROVED','REJECTED','RESOLVED','PUNISHED') NOT NULL,
  `decision` VARCHAR(500) NULL,
  `handled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_moderation_records_report` (`report_id`),
  KEY `idx_moderation_records_target` (`target_type`,`target_id`),
  KEY `idx_moderation_records_handler` (`handler_id`),
  CONSTRAINT `fk_moderation_records_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`),
  CONSTRAINT `fk_moderation_records_handler` FOREIGN KEY (`handler_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治理处理记录';


