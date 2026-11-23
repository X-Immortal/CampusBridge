-- 演示用户：已通过校园认证，可以直接发布任务。
-- 真实环境下由注册 + 认证流程产生，这里只是让 M1 的现场演示不依赖外部服务。

INSERT INTO users (username, password_hash, email, role, enabled)
VALUES ('demo_student', 'not-a-real-hash', 'demo@example.edu.cn', 'USER', 1);

INSERT INTO user_profiles (user_id, nickname, campus, college)
VALUES (1, '演示同学', '主校区', '软件工程学院');

INSERT INTO campus_verifications (
  user_id, method, real_name_cipher, student_number_cipher, student_number_hash,
  school, department, status, created_at, reviewed_at
) VALUES (
  1, 'CAMPUS_EMAIL', 'encrypted-name', 'encrypted-number', 'demo-student-number-hash',
  '示例大学', '软件工程学院', 'VERIFIED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
