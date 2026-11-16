SET SQL_SAFE_UPDATES = 0;
USE campusbridge;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- clear old data
DELETE FROM moderation_records;
DELETE FROM reports;
DELETE FROM attachments;
DELETE FROM notifications;
DELETE FROM messages;
DELETE FROM conversations;
DELETE FROM reviews;
DELETE FROM task_histories;
DELETE FROM completion_proofs;
DELETE FROM task_applications;
DELETE FROM tasks;
DELETE FROM post_interactions;
DELETE FROM replies;
DELETE FROM posts;
DELETE FROM credit_records;
DELETE FROM user_blocks;
DELETE FROM campus_verifications;
DELETE FROM user_credits;
DELETE FROM user_profiles;
DELETE FROM users;

ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE user_profiles AUTO_INCREMENT = 1;
ALTER TABLE campus_verifications AUTO_INCREMENT = 1;
ALTER TABLE user_credits AUTO_INCREMENT = 1;
ALTER TABLE credit_records AUTO_INCREMENT = 1;
ALTER TABLE posts AUTO_INCREMENT = 1;
ALTER TABLE replies AUTO_INCREMENT = 1;
ALTER TABLE post_interactions AUTO_INCREMENT = 1;
ALTER TABLE tasks AUTO_INCREMENT = 1;
ALTER TABLE task_applications AUTO_INCREMENT = 1;
ALTER TABLE completion_proofs AUTO_INCREMENT = 1;
ALTER TABLE task_histories AUTO_INCREMENT = 1;
ALTER TABLE reviews AUTO_INCREMENT = 1;
ALTER TABLE conversations AUTO_INCREMENT = 1;
ALTER TABLE messages AUTO_INCREMENT = 1;
ALTER TABLE notifications AUTO_INCREMENT = 1;
ALTER TABLE attachments AUTO_INCREMENT = 1;
ALTER TABLE reports AUTO_INCREMENT = 1;
ALTER TABLE moderation_records AUTO_INCREMENT = 1;
ALTER TABLE user_blocks AUTO_INCREMENT = 1;


DROP TEMPORARY TABLE IF EXISTS seed_users;
CREATE TEMPORARY TABLE seed_users (
  id BIGINT UNSIGNED PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  campus VARCHAR(100) NOT NULL,
  college VARCHAR(100) NOT NULL,
  bio VARCHAR(200) NOT NULL,
  email VARCHAR(120) NOT NULL,
  interest_tags_json JSON NOT NULL,
  skill_tags_json JSON NOT NULL,
  role ENUM('USER','ADMIN') NOT NULL,
  verify_status ENUM('VERIFIED','PENDING','UNVERIFIED') NOT NULL,
  points INT NOT NULL,
  level INT NOT NULL
);

INSERT INTO seed_users (
  id, username, nickname, campus, college, bio, email,
  interest_tags_json, skill_tags_json, role, verify_status, points, level
) VALUES
(1, 'admin', '平台管理员', '仙林', '软件学院', '负责认证审核与日常治理。', 'admin@campusbridge.test', JSON_ARRAY('治理', '审核'), JSON_ARRAY('协调', '值班'), 'ADMIN', 'VERIFIED', 100, 4),
(2, 'chenyue', '陈月', '仙林', '软件学院', '常在图书馆，偶尔接顺路小任务。', 'chenyue@smail.nju.edu.cn', JSON_ARRAY('跑腿', '摄影'), JSON_ARRAY('前端', '文档'), 'USER', 'VERIFIED', 92, 4),
(3, 'liangfan', '梁帆', '仙林', '计算机学院', '喜欢折腾设备，也常帮人配环境。', 'liangfan@smail.nju.edu.cn', JSON_ARRAY('技术', '二手'), JSON_ARRAY('Linux', '算法'), 'USER', 'VERIFIED', 88, 3),
(4, 'sunqi', '孙琪', '鼓楼', '商学院', '办事认真，擅长流程类代办。', 'sunqi@smail.nju.edu.cn', JSON_ARRAY('代办', '表格'), JSON_ARRAY('Excel', '沟通'), 'USER', 'VERIFIED', 84, 3),
(5, 'zhouting', '周婷', '仙林', '新闻传播学院', '会写活动推文，也会帮忙拍照。', 'zhouting@smail.nju.edu.cn', JSON_ARRAY('活动', '写作'), JSON_ARRAY('摄影', '宣传'), 'USER', 'VERIFIED', 86, 3),
(6, 'wanghao', '王昊', '仙林', '物理学院', '实验室和快递站两头跑。', 'wanghao@smail.nju.edu.cn', JSON_ARRAY('快递', '实验'), JSON_ARRAY('搬运', '整理'), 'USER', 'VERIFIED', 79, 2),
(7, 'lujia', '陆嘉', '鼓楼', '医学院', '经常帮同学带药和代交材料。', 'lujia@smail.nju.edu.cn', JSON_ARRAY('医疗', '跑腿'), JSON_ARRAY('耐心', '执行'), 'USER', 'VERIFIED', 81, 3),
(8, 'helin', '何霖', '仙林', '外国语学院', '想找语言搭子，也会答疑。', 'helin@smail.nju.edu.cn', JSON_ARRAY('英语', '法语'), JSON_ARRAY('口语', '翻译'), 'USER', 'VERIFIED', 76, 2),
(9, 'gaoyuan', '高远', '仙林', '地理与海洋科学学院', '喜欢骑车，顺路任务接得多。', 'gaoyuan@smail.nju.edu.cn', JSON_ARRAY('骑行', '地图'), JSON_ARRAY('跑腿', '导航'), 'USER', 'VERIFIED', 83, 3),
(10, 'pengxin', '彭欣', '仙林', '文学院', '二手书和课程资料整理达人。', 'pengxin@smail.nju.edu.cn', JSON_ARRAY('二手书', '课程'), JSON_ARRAY('整理', '答疑'), 'USER', 'VERIFIED', 74, 2),
(11, 'tangyi', '唐逸', '鼓楼', '法学院', '擅长流程提醒和规则说明。', 'tangyi@smail.nju.edu.cn', JSON_ARRAY('办事', '咨询'), JSON_ARRAY('法条检索', '沟通'), 'USER', 'VERIFIED', 90, 4),
(12, 'xuyun', '徐云', '仙林', '环境学院', '社团事务多，经常发组队帖。', 'xuyun@smail.nju.edu.cn', JSON_ARRAY('社团', '公益'), JSON_ARRAY('组织', '提醒'), 'USER', 'VERIFIED', 73, 2),
(13, 'shaoan', '邵安', '仙林', '工程管理学院', '擅长借工具和修小东西。', 'shaoan@smail.nju.edu.cn', JSON_ARRAY('工具', '维修'), JSON_ARRAY('焊接', '安装'), 'USER', 'VERIFIED', 77, 2),
(14, 'qiaonan', '乔楠', '鼓楼', '历史学院', '爱逛二手群，也爱参加讲座。', 'qiaonan@smail.nju.edu.cn', JSON_ARRAY('讲座', '二手'), JSON_ARRAY('沟通', '记录'), 'USER', 'VERIFIED', 72, 2),
(15, 'duzhe', '杜哲', '仙林', '数学学院', '经常帮低年级同学看作业。', 'duzhe@smail.nju.edu.cn', JSON_ARRAY('数学', '答疑'), JSON_ARRAY('高数', '线代'), 'USER', 'VERIFIED', 89, 3),
(16, 'fuying', '傅莹', '仙林', '化学化工学院', '实验课多，常求顺路带物。', 'fuying@smail.nju.edu.cn', JSON_ARRAY('实验', '代拿'), JSON_ARRAY('细致', '协作'), 'USER', 'VERIFIED', 71, 2),
(17, 'renke', '任可', '仙林', '人工智能学院', '乐于接配环境和脚本小单。', 'renke@smail.nju.edu.cn', JSON_ARRAY('脚本', 'Linux'), JSON_ARRAY('Python', '部署'), 'USER', 'VERIFIED', 95, 4),
(18, 'maomao', '毛茂', '鼓楼', '社会学院', '社调问卷和访谈互助积极。', 'maomao@smail.nju.edu.cn', JSON_ARRAY('问卷', '访谈'), JSON_ARRAY('采访', '整理'), 'USER', 'VERIFIED', 75, 2),
(19, 'yexin', '叶欣', '仙林', '生命科学学院', '作息早，适合接早场任务。', 'yexin@smail.nju.edu.cn', JSON_ARRAY('早起', '实验'), JSON_ARRAY('守时', '细心'), 'USER', 'VERIFIED', 82, 3),
(20, 'linmo', '林墨', '仙林', '哲学系', '会逛展，也会一起去讲座。', 'linmo@smail.nju.edu.cn', JSON_ARRAY('讲座', '阅读'), JSON_ARRAY('记录', '交流'), 'USER', 'VERIFIED', 69, 2),
(21, 'songran', '宋然', '鼓楼', '建筑与城市规划学院', '图纸打印和模型材料经验多。', 'songran@smail.nju.edu.cn', JSON_ARRAY('打印', '模型'), JSON_ARRAY('排版', '采购'), 'USER', 'VERIFIED', 80, 3),
(22, 'jiangnan', '江楠', '仙林', '电子科学与工程学院', '帮忙借板子、买元件很熟。', 'jiangnan@smail.nju.edu.cn', JSON_ARRAY('硬件', '元件'), JSON_ARRAY('焊接', '调试'), 'USER', 'VERIFIED', 78, 2),
(23, 'taole', '陶乐', '仙林', '教育研究院', '爱组织自习搭子和复习群。', 'taole@smail.nju.edu.cn', JSON_ARRAY('自习', '复习'), JSON_ARRAY('组织', '提醒'), 'USER', 'VERIFIED', 68, 2),
(24, 'guwen', '顾雯', '鼓楼', '国际关系学院', '常帮交换生处理校园事务。', 'guwen@smail.nju.edu.cn', JSON_ARRAY('国际生', '咨询'), JSON_ARRAY('英文邮件', '沟通'), 'USER', 'VERIFIED', 87, 3),
(25, 'luchen', '陆宸', '仙林', '天文与空间科学学院', '夜猫子，适合晚间紧急任务。', 'luchen@smail.nju.edu.cn', JSON_ARRAY('夜跑', '急单'), JSON_ARRAY('守时', '搬运'), 'USER', 'VERIFIED', 85, 3),
(26, 'xieyu', '谢雨', '仙林', '管理学院', '做事稳，喜欢接资料整理任务。', 'xieyu@smail.nju.edu.cn', JSON_ARRAY('资料', '代办'), JSON_ARRAY('表格', '归档'), 'USER', 'VERIFIED', 70, 2),
(27, 'yiran', '依然', '仙林', '软件学院', '刚注册，资料已提交审核。', 'yiran@smail.nju.edu.cn', JSON_ARRAY('新生', '问路'), JSON_ARRAY('沟通', '记录'), 'USER', 'PENDING', 55, 1),
(28, 'caixi', '蔡溪', '鼓楼', '商学院', '准备开始接一些小任务。', 'caixi@smail.nju.edu.cn', JSON_ARRAY('表格', '学习'), JSON_ARRAY('整理', '执行'), 'USER', 'PENDING', 58, 1),
(29, 'qimu', '齐木', '仙林', '信息管理学院', '刚来平台，先看看帖子。', 'qimu@smail.nju.edu.cn', JSON_ARRAY('二手', '咨询'), JSON_ARRAY('检索', '写作'), 'USER', 'UNVERIFIED', 48, 1),
(30, 'anran', '安然', '鼓楼', '文学院', '平时潜水，偶尔回帖。', 'anran@smail.nju.edu.cn', JSON_ARRAY('讲座', '书单'), JSON_ARRAY('校对', '记录'), 'USER', 'UNVERIFIED', 46, 1);

DROP TEMPORARY TABLE IF EXISTS seed_rounds;
CREATE TEMPORARY TABLE seed_rounds (
  round_idx INT PRIMARY KEY
);

INSERT INTO seed_rounds (round_idx) VALUES (0), (1), (2), (3), (4);

DROP TEMPORARY TABLE IF EXISTS seed_post_templates;
CREATE TEMPORARY TABLE seed_post_templates (
  template_id INT PRIMARY KEY,
  category ENUM('INQUIRY_HELP','SECOND_HAND','TEAM_FRIEND','CAMPUS_SOCIAL') NOT NULL,
  title VARCHAR(100) NOT NULL,
  content VARCHAR(200) NOT NULL
);

INSERT INTO seed_post_templates (template_id, category, title, content) VALUES
(1, 'INQUIRY_HELP', '仙林校医院周末能开过敏药吗', '最近花粉过敏，有同学周末去过校医院吗，想确认挂号和取药流程。'),
(2, 'SECOND_HAND', '出九成新高数教材', '同济高数上下册，笔记不多，仙林面交。'),
(3, 'TEAM_FRIEND', '找数模暑期组队伙伴', '想找会建模或写论文的同学，暑假一起准备比赛。'),
(4, 'CAMPUS_SOCIAL', '求推荐鼓楼附近安静自习点', '周末去鼓楼校区办事，想顺便找个能坐半天的地方。'),
(5, 'INQUIRY_HELP', '请问图书馆储物柜怎么申请', '新学期想租长期柜子，不知道入口和时长。'),
(6, 'SECOND_HAND', '转让闲置台灯', '宿舍换了新灯，这个还能正常用，带原盒。'),
(7, 'TEAM_FRIEND', '找晨跑搭子', '仙林操场工作日早上六点半左右，有人一起吗。'),
(8, 'CAMPUS_SOCIAL', '今晚操场散步有人吗', '写了一天代码，想出去走走顺便聊聊天。'),
(9, 'INQUIRY_HELP', '课程退补选有人了解吗', '一门公选课时间冲突，想问退补选窗口大概什么时候开。'),
(10, 'SECOND_HAND', '出闲置计算器', '考试用的卡西欧，外观正常，功能完好。');

DROP TEMPORARY TABLE IF EXISTS seed_task_templates;
CREATE TEMPORARY TABLE seed_task_templates (
  template_id INT PRIMARY KEY,
  category ENUM('ERRAND','AGENCY') NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(200) NOT NULL,
  campus VARCHAR(50) NOT NULL,
  location VARCHAR(120) NOT NULL,
  delivery_location VARCHAR(120) NULL,
  reward_type ENUM('FREE','POINTS','CASH','NEGOTIABLE') NOT NULL,
  reward_description VARCHAR(200) NOT NULL,
  reward_amount INT NULL,
  completion_standard VARCHAR(200) NOT NULL,
  requires_proof TINYINT(1) NOT NULL,
  allow_offline_contact TINYINT(1) NOT NULL,
  is_instant_task TINYINT(1) NOT NULL
);

INSERT INTO seed_task_templates (
  template_id, category, title, description, campus, location, delivery_location,
  reward_type, reward_description, reward_amount, completion_standard,
  requires_proof, allow_offline_contact, is_instant_task
) VALUES
(1, 'ERRAND', '取仙林快递站包裹', '帮我把包裹从快递站带到食堂门口，晚上八点前都行。', '仙林', '仙林快递站 3 号架', '十食堂门口', 'CASH', '12元', 12, '送达后在站内发消息确认。', 1, 0, 1),
(2, 'AGENCY', '代交纸质报名表', '报名表和附件已准备好，只需要帮忙在截止前交到窗口。', '仙林', '仙林行政楼 A108', NULL, 'POINTS', '40积分', 40, '办完后说明结果，如有回执请拍照。', 1, 0, 0),
(3, 'ERRAND', '帮带一份晚饭到宿舍楼下', '想要一份清淡晚饭，到了给我发消息。', '鼓楼', '九食堂', '5 栋宿舍楼下', 'CASH', '10元', 10, '送达后在站内发消息确认。', 0, 0, 1),
(4, 'AGENCY', '帮问教务处一个流程问题', '想确认缓考申请材料是否需要盖章，帮忙问清楚即可。', '鼓楼', '鼓楼教务窗口', NULL, 'FREE', '口头感谢', 0, '办完后说明结果，如有回执请拍照。', 0, 0, 0),
(5, 'ERRAND', '顺路带一瓶感冒药', '帮忙买常见感冒药，费用我报销。', '仙林', '校医院旁药房', '六食堂门口', 'NEGOTIABLE', '药费实报加辛苦费', 15, '送达后在站内发消息确认。', 0, 1, 1),
(6, 'AGENCY', '代办图书馆借阅证补办', '学生证在我这里，需要帮忙排队办理。', '仙林', '图书馆服务台', NULL, 'CASH', '15元', 15, '办完后说明结果，如有回执请拍照。', 0, 0, 0),
(7, 'ERRAND', '代拿打印好的材料', '材料已经下单，只需要顺路带回来。', '仙林', '教学楼打印店', '图书馆北门', 'POINTS', '30积分', 30, '送达后在站内发消息确认。', 0, 0, 0),
(8, 'AGENCY', '帮送社团海报到学院办', '海报已经打印好，下午四点前送到即可。', '鼓楼', '软件楼辅导员办公室', NULL, 'POINTS', '25积分', 25, '办完后说明结果，如有回执请拍照。', 0, 0, 0),
(9, 'ERRAND', '帮取实验耗材', '老师放在值班室了，取到后放前台即可。', '仙林', '理科楼值班室', '化院大厅', 'FREE', '请喝奶茶', 0, '送达后在站内发消息确认。', 1, 0, 0),
(10, 'AGENCY', '代领讲座签到纪念品', '我临时有事去不了，想请人凭截图代领。', '仙林', '鼓楼逸夫馆服务台', NULL, 'NEGOTIABLE', '可协商', 20, '办完后说明结果，如有回执请拍照。', 0, 1, 0);

-- password for all accounts: password
INSERT INTO users (id, username, password_hash, phone_cipher, phone_hash, email, role, enabled, created_at, updated_at)
SELECT
  su.id,
  su.username,
  '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6q7Q6nQ1OtSWT8gJtIhXCTITVEWil',
  CONCAT('1380000', LPAD(su.id, 4, '0')),
  SHA2(CONCAT('1380000', LPAD(su.id, 4, '0')), 256),
  su.email,
  su.role,
  1,
  DATE_ADD('2026-08-15 09:00:00', INTERVAL su.id HOUR),
  DATE_ADD('2026-08-15 09:00:00', INTERVAL su.id HOUR)
FROM seed_users su
ORDER BY su.id;

INSERT INTO user_profiles (
  id, user_id, nickname, bio, campus, college, contact_info, avatar_url,
  interest_tags, skill_tags, created_at, updated_at
)
SELECT
  su.id,
  su.id,
  su.nickname,
  su.bio,
  su.campus,
  su.college,
  CONCAT('站内联系优先 / ', su.email),
  CONCAT('https://campusbridge.example/avatar/u', su.id, '.png'),
  su.interest_tags_json,
  su.skill_tags_json,
  DATE_ADD('2026-08-15 09:00:00', INTERVAL su.id HOUR),
  DATE_ADD('2026-08-15 09:00:00', INTERVAL su.id HOUR)
FROM seed_users su
ORDER BY su.id;

INSERT INTO campus_verifications (
  id, user_id, method, real_name_cipher, student_number_cipher, student_number_hash,
  school, department, evidence_url, status, reviewer_id, reject_reason, created_at, reviewed_at
)
SELECT
  su.id,
  su.id,
  CASE MOD(su.id, 3)
    WHEN 0 THEN 'CAMPUS_EMAIL'
    WHEN 1 THEN 'STUDENT_CARD_IMAGE'
    ELSE 'MANUAL_REVIEW'
  END,
  su.nickname,
  CONCAT('2026', LPAD(su.id, 4, '0')),
  SHA2(CONCAT('2026', LPAD(su.id, 4, '0')), 256),
  '南京大学',
  su.college,
  CASE
    WHEN MOD(su.id, 3) = 0 THEN NULL
    ELSE CONCAT('https://campusbridge.example/verify/u', su.id, '.jpg')
  END,
  su.verify_status,
  CASE WHEN su.verify_status = 'VERIFIED' THEN 1 ELSE NULL END,
  NULL,
  DATE_ADD('2026-08-20 10:00:00', INTERVAL su.id HOUR),
  CASE
    WHEN su.verify_status = 'VERIFIED'
    THEN DATE_ADD('2026-08-21 10:00:00', INTERVAL su.id HOUR)
    ELSE NULL
  END
FROM seed_users su
WHERE su.verify_status <> 'UNVERIFIED'
ORDER BY su.id;

INSERT INTO user_credits (id, user_id, points, level, updated_at)
SELECT su.id, su.id, su.points, su.level, '2026-09-20 12:00:00'
FROM seed_users su
ORDER BY su.id;

INSERT INTO credit_records (user_id, change_value, reason, source_type, source_id, created_at)
SELECT
  su.id,
  su.points,
  'seed_init',
  'SYSTEM',
  su.id,
  '2026-09-20 12:00:00'
FROM seed_users su
ORDER BY su.id;

INSERT INTO posts (
  id, author_id, category, title, content, status, reply_count, like_count, mark_count,
  accepted_reply_id, created_at, updated_at
)
SELECT
  sr.round_idx * 10 + spt.template_id AS id,
  MOD(sr.round_idx * 10 + spt.template_id - 1, 29) + 2 AS author_id,
  spt.category,
  CASE
    WHEN sr.round_idx = 0 THEN spt.title
    ELSE CONCAT(spt.title, ' #', sr.round_idx * 10 + spt.template_id)
  END,
  spt.content,
  'PUBLISHED',
  0,
  0,
  0,
  NULL,
  DATE_ADD('2026-09-01 09:00:00', INTERVAL ((sr.round_idx * 10 + spt.template_id) - 1) * 6 HOUR),
  DATE_ADD('2026-09-01 09:00:00', INTERVAL ((sr.round_idx * 10 + spt.template_id) - 1) * 6 HOUR)
FROM seed_rounds sr
CROSS JOIN seed_post_templates spt
ORDER BY id;

INSERT INTO tasks (
  id, requester_id, provider_id, category, title, description, campus, location, delivery_location,
  deadline, reward_type, reward_description, reward_amount, completion_standard, requires_proof,
  only_in_app_contact, allow_offline_contact, is_instant_task, route_hint,
  risk_disclosure_confirmed, sensitive_reason, status, created_at, updated_at
)
SELECT
  sr.round_idx * 10 + stt.template_id AS id,
  MOD(sr.round_idx * 10 + stt.template_id - 1, 25) + 2 AS requester_id,
  CASE
    WHEN sr.round_idx * 10 + stt.template_id BETWEEN 26 AND 49
    THEN 9 + MOD(sr.round_idx * 10 + stt.template_id - 26, 12)
    ELSE NULL
  END AS provider_id,
  stt.category,
  CASE
    WHEN sr.round_idx = 0 THEN stt.title
    ELSE CONCAT(stt.title, ' #', sr.round_idx * 10 + stt.template_id)
  END,
  stt.description,
  stt.campus,
  stt.location,
  stt.delivery_location,
  CASE
    WHEN sr.round_idx * 10 + stt.template_id <= 20
      THEN DATE_ADD(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 2 DAY)
    WHEN sr.round_idx * 10 + stt.template_id <= 25
      THEN DATE_ADD(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 3 DAY)
    WHEN sr.round_idx * 10 + stt.template_id <= 40
      THEN DATE_ADD(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 1 DAY)
    WHEN sr.round_idx * 10 + stt.template_id <= 44
      THEN DATE_ADD(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 12 HOUR)
    WHEN sr.round_idx * 10 + stt.template_id = 50
      THEN DATE_ADD(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 1 DAY)
    ELSE DATE_SUB(DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR), INTERVAL 8 HOUR)
  END AS deadline,
  stt.reward_type,
  stt.reward_description,
  stt.reward_amount,
  stt.completion_standard,
  stt.requires_proof,
  1,
  stt.allow_offline_contact,
  stt.is_instant_task,
  NULL,
  0,
  NULL,
  CASE
    WHEN sr.round_idx * 10 + stt.template_id <= 20 THEN 'OPEN'
    WHEN sr.round_idx * 10 + stt.template_id <= 25 THEN 'PENDING_REVIEW'
    WHEN sr.round_idx * 10 + stt.template_id <= 33 THEN 'ACCEPTED'
    WHEN sr.round_idx * 10 + stt.template_id <= 40 THEN 'IN_PROGRESS'
    WHEN sr.round_idx * 10 + stt.template_id <= 44 THEN 'PENDING_CONFIRMATION'
    WHEN sr.round_idx * 10 + stt.template_id <= 48 THEN 'REVIEWED'
    WHEN sr.round_idx * 10 + stt.template_id = 49 THEN 'COMPLETED'
    ELSE 'CANCELLED'
  END AS status,
  DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR),
  DATE_ADD('2026-09-03 08:00:00', INTERVAL ((sr.round_idx * 10 + stt.template_id) - 1) * 5 HOUR)
FROM seed_rounds sr
CROSS JOIN seed_task_templates stt
ORDER BY id;

INSERT INTO completion_proofs (id, task_id, submitter_id, description, submitted_at)
SELECT
  t.id,
  t.id,
  COALESCE(t.provider_id, t.requester_id),
  '已按要求完成，相关截图已在站内说明。',
  DATE_ADD(t.created_at, INTERVAL 1 DAY)
FROM tasks t
WHERE t.id BETWEEN 41 AND 49
ORDER BY t.id;

INSERT INTO task_histories (id, task_id, from_status, to_status, operator_id, event_type, note, created_at)
SELECT
  t.id - 20,
  t.id,
  CASE t.status
    WHEN 'ACCEPTED' THEN 'OPEN'
    WHEN 'IN_PROGRESS' THEN 'ACCEPTED'
    WHEN 'PENDING_CONFIRMATION' THEN 'IN_PROGRESS'
    WHEN 'REVIEWED' THEN 'COMPLETED'
    WHEN 'COMPLETED' THEN 'PENDING_CONFIRMATION'
    WHEN 'CANCELLED' THEN 'OPEN'
    WHEN 'DISPUTED' THEN 'IN_PROGRESS'
    WHEN 'REJECTED' THEN 'PENDING_REVIEW'
    ELSE NULL
  END,
  t.status,
  t.requester_id,
  CASE t.status
    WHEN 'DRAFT' THEN 'DRAFT_SAVED'
    WHEN 'ACCEPTED' THEN 'APPLICATION_ACCEPTED'
    WHEN 'IN_PROGRESS' THEN 'STARTED'
    WHEN 'PENDING_CONFIRMATION' THEN 'COMPLETION_PROOF_SUBMITTED'
    WHEN 'REVIEWED' THEN 'REVIEWED'
    WHEN 'COMPLETED' THEN 'COMPLETION_CONFIRMED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'DISPUTED' THEN 'DISPUTED'
    WHEN 'REJECTED' THEN 'MODERATION_REJECTED'
    ELSE 'CREATED'
  END,
  CASE t.status
    WHEN 'PENDING_REVIEW' THEN '等待管理员审核'
    WHEN 'ACCEPTED' THEN '已选定帮忙同学'
    WHEN 'IN_PROGRESS' THEN '执行中'
    WHEN 'PENDING_CONFIRMATION' THEN '已提交完成说明'
    WHEN 'REVIEWED' THEN '双方已互评'
    WHEN 'COMPLETED' THEN '已完成待评价'
    WHEN 'CANCELLED' THEN '发布者取消任务'
    ELSE '状态更新'
  END,
  DATE_ADD(t.created_at, INTERVAL 6 HOUR)
FROM tasks t
WHERE t.status <> 'OPEN'
ORDER BY t.id;

INSERT INTO reviews (id, task_id, reviewer_id, reviewee_id, rating, comment, created_at)
SELECT
  (t.id - 45) * 2 + 1,
  t.id,
  t.requester_id,
  t.provider_id,
  5,
  '沟通顺畅，完成很准时。',
  DATE_ADD(t.created_at, INTERVAL 2 DAY)
FROM tasks t
WHERE t.id BETWEEN 45 AND 48
UNION ALL
SELECT
  (t.id - 45) * 2 + 2,
  t.id,
  t.provider_id,
  t.requester_id,
  5,
  '需求描述清楚，确认也及时。',
  DATE_ADD(DATE_ADD(t.created_at, INTERVAL 2 DAY), INTERVAL 5 MINUTE)
FROM tasks t
WHERE t.id BETWEEN 45 AND 48;

SET FOREIGN_KEY_CHECKS = 1;

DROP TEMPORARY TABLE IF EXISTS seed_task_templates;
DROP TEMPORARY TABLE IF EXISTS seed_post_templates;
DROP TEMPORARY TABLE IF EXISTS seed_rounds;
DROP TEMPORARY TABLE IF EXISTS seed_users;

UPDATE users
SET password_hash = '$2a$10$9np3w/XWv3K9KeiE9Spm4.OfH4S92EGN8A3jhOIetisF8QD5YOSeC',
    updated_at = NOW();

SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS post_count FROM posts;
SELECT COUNT(*) AS task_count FROM tasks;
SELECT COUNT(*) AS verified_user_count FROM campus_verifications WHERE status = 'VERIFIED';
