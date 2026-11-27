-- Load after classpath:db/demo-data.sql in a fresh, disposable H2 demo.
-- The task read mapper joins user_profiles, which the assistant-only demo omits.
CREATE TABLE IF NOT EXISTS user_profiles (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  nickname VARCHAR(50) NOT NULL
);

INSERT INTO users (username, password_hash)
VALUES ('m4_fixture_provider', 'unused-fixture-password-hash');

INSERT INTO user_profiles (user_id, nickname)
SELECT id, 'M4 fixture provider' FROM users WHERE username = 'm4_fixture_provider';

INSERT INTO tasks (
  requester_id, provider_id, category, title, description, campus, location,
  deadline, reward_type, completion_standard, route_hint, sensitive_reason, status
)
SELECT 1, id, 'ERRAND', 'M4_VISITOR_PROJECTION_FIXTURE', 'synthetic task', '主校区', '北门',
       DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'FREE', 'synthetic completion',
       'synthetic private route', 'synthetic private reason', 'ACCEPTED'
FROM users WHERE username = 'm4_fixture_provider';
