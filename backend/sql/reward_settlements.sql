-- 奖励结算表
CREATE TABLE `reward_settlements` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT UNSIGNED NOT NULL COMMENT '关联任务ID',
  `requester_id` BIGINT UNSIGNED NOT NULL COMMENT '求助者ID',
  `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '帮助者ID',
  `reward_type` VARCHAR(50) NOT NULL COMMENT '奖励类型：POINTS, NEGOTIABLE',
  `points_amount` INT NULL COMMENT '积分数量（POINTS类型时）',
  `negotiated_amount` DECIMAL(10,2) NULL COMMENT '协商金额（NEGOTIABLE类型时）',
  `settlement_status` ENUM('PENDING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'COMPLETED',
  `settled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_settlements_task` (`task_id`),
  KEY `idx_reward_settlements_provider` (`provider_id`,`settled_at`),
  KEY `idx_reward_settlements_requester` (`requester_id`,`settled_at`),
  CONSTRAINT `fk_reward_settlements_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`),
  CONSTRAINT `fk_reward_settlements_requester` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_reward_settlements_provider` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励结算记录';
