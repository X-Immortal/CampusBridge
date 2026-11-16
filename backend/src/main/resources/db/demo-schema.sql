-- M1 演示库：只建「对话建单」这一条链路会用到的最小表集。
-- 生产仍以 backend/sql/schema.sql（MySQL）为准，这份只在 demo profile 下使用。
-- 与 MySQL 版的差别：ENUM 降级成 VARCHAR、去掉 ENGINE/CHARSET/UNSIGNED —— H2 也能跑。

CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone_cipher VARCHAR(255),
  phone_hash CHAR(64),
  email VARCHAR(120),
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS campus_verifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  method VARCHAR(30) NOT NULL,
  real_name_cipher VARCHAR(255) NOT NULL,
  student_number_cipher VARCHAR(255) NOT NULL,
  student_number_hash CHAR(64),
  school VARCHAR(100) NOT NULL,
  department VARCHAR(100),
  evidence_url VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT,
  reject_reason VARCHAR(300),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  requester_id BIGINT NOT NULL,
  provider_id BIGINT,
  category VARCHAR(20) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  campus VARCHAR(50) NOT NULL,
  location VARCHAR(120) NOT NULL,
  delivery_location VARCHAR(120),
  deadline TIMESTAMP NOT NULL,
  reward_type VARCHAR(20) NOT NULL,
  reward_description VARCHAR(200),
  reward_amount INT,
  completion_standard VARCHAR(500) NOT NULL,
  requires_proof TINYINT NOT NULL DEFAULT 0,
  only_in_app_contact TINYINT NOT NULL DEFAULT 1,
  allow_offline_contact TINYINT NOT NULL DEFAULT 0,
  is_instant_task TINYINT NOT NULL DEFAULT 0,
  route_hint VARCHAR(300),
  risk_disclosure_confirmed TINYINT NOT NULL DEFAULT 0,
  sensitive_reason VARCHAR(300),
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_histories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  operator_id BIGINT,
  note VARCHAR(300),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
