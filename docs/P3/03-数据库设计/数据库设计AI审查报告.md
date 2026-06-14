# 数据库设计 AI 审查报告

## 1. 审查对象

本报告审查对象为数据库设计文件：

| 文件 | 说明 |
|---|---|
| `schema.sql` | 建表 SQL |
| `ER图总览.png` | ER 图 |
| `数据库设计说明.md` | 数据库设计说明 |

审查重点按照 P3 阶段要求展开，包括第三范式、索引合理性、潜在性能瓶颈、隐私数据处理和业务完整性。

## 2. 总体结论

数据库设计整体完整，能够覆盖 P3 要求中的核心实体、关系、主键、外键、索引和隐私处理说明。

主要设计结论如下：

- `posts` 支持社区型内容的分类、状态、回复统计、互动统计和采纳回复。
- `tasks` 支持任务分类、校区、地点、截止时间、奖励方式、完成标准、凭证要求和任务状态机。
- `task_histories` 记录 `from_status`、`to_status`、`operator_id`、`note`，可以追踪完整状态流转。
- `conversations` 和 `messages` 支持会话类型、关联对象、是否可回复、消息类型、阅读时间等信息。
- `attachments` 支持附件上传后的临时状态和业务绑定。
- `reviews` 支持双向评价，并通过唯一索引防止重复评价。
- 敏感信息不再直接以明文字段作为主要存储方式，增加了密文字段和哈希字段。

综合判断：数据库设计满足 P3 数据库设计提交要求。

## 3. 第三范式审查

### 3.1 审查结论

整体满足第三范式。

主要依据：

- 各表围绕单一实体建模，例如 `posts` 只保存帖子主体，`replies` 保存回复，`tasks` 保存任务主体，`task_histories` 保存状态历史。
- 用户账号、公开资料、认证信息、积分结果、积分流水被拆分为不同表，避免 `users` 表字段过多。
- 任务主表没有直接保存申请列表、历史列表、评价列表，而是由独立表表达一对多关系。
- 展示对象如 `ContentItemView`、`TaskDetailView`、`UserHomepageView` 没有被错误建成持久化表，而是由查询聚合产生。

### 3.2 需要注意的地方

`posts.reply_count`、`posts.like_count`、`posts.mark_count` 属于冗余统计字段，严格来说不是完全规范化字段。但是这些字段用于高频列表展示，可以接受。实现时需要通过事务、异步事件或定期校准机制保证统计值与明细表一致。

`attachments`、`reports`、`moderation_records` 使用 `owner_type/target_type + id` 的多态关联，数据库无法通过物理外键保证目标对象一定存在。这是为了跨模块扩展性做出的折中，需要应用层严格校验。

## 4. 索引设计审查

### 4.1 合理之处

索引基本覆盖主要查询场景：

| 查询场景 | 对应索引 |
|---|---|
| 用户登录 | `uk_users_username` |
| 手机号查重 | `uk_users_phone_hash` |
| 首页/板块帖子列表 | `idx_posts_category_status_created` |
| 帖子回复加载 | `idx_replies_post_created` |
| 点赞/标记去重 | `uk_post_interactions_user_target_type` |
| 任务列表筛选 | `idx_tasks_category_status_created` |
| 即将截止任务查询 | `idx_tasks_status_deadline` |
| 用户发布/承接任务 | `idx_tasks_requester`、`idx_tasks_provider` |
| 任务状态历史 | `idx_task_histories_task_created` |
| 会话列表 | `idx_conversations_owner_last` |
| 会话消息分页 | `idx_messages_conversation_created` |
| 附件加载 | `idx_attachments_owner` |
| 举报处理列表 | `idx_reports_status_created` |

这些索引与列表、详情、筛选、分页等常见访问场景基本匹配。

### 4.2 优化建议

`posts.content`、`tasks.description` 等正文暂未建立全文索引。若需要支持关键词搜索，建议采用 MySQL FULLTEXT 或独立搜索服务。

`idx_posts_status_created` 和 `idx_posts_category_status_created` 存在部分覆盖关系。在当前阶段可以保留；若写入压力变大，应根据实际查询计划精简。

`attachments` 的临时文件清理依赖 `idx_attachments_uploader_status`。若需要按时间批量清理，建议增加 `(status, created_at)` 索引。

## 5. 性能瓶颈审查

### 5.1 无明显严重瓶颈

核心列表查询都具备可用索引：

- 帖子列表按分类、状态、创建时间查询。
- 任务列表按分类、状态、校区、截止时间查询。
- 会话消息按会话和创建时间分页。
- 任务历史按任务和创建时间查询。

模块拆分也较清晰，不会出现单表承载所有需求导致大量可空字段和低效筛选的问题。

### 5.2 潜在风险

第一，统一内容流 `/content` 需要同时查询 `posts` 和 `tasks`。数据库中没有单独的 `content_items` 表，因此实现时一般需要分别查询 `posts`、`tasks` 后在应用层合并排序，或者使用 `UNION ALL`。这符合“统一展示，分别建模”的设计，但需要注意分页策略。

第二，`attachments` 和 `reports` 的多态关联查询需要根据类型分派。目标类型增加时，应封装统一的附件服务和举报服务，避免业务代码分散处理类型判断。

第三，统计字段如 `like_count`、`reply_count` 在高并发下可能出现更新竞争。在当前阶段风险可控；真实上线时可采用异步计数或定期修正。

## 6. 隐私与安全审查

### 6.1 合理之处

数据库设计已经注意到隐私数据不能简单明文保存：

- 密码保存为 `password_hash`。
- 手机号拆分为 `phone_cipher` 和 `phone_hash`。
- 真实姓名保存为 `real_name_cipher`。
- 学号保存为 `student_number_cipher` 和 `student_number_hash`。
- 用户主页只应展示认证摘要，而不是认证原始信息。

这种设计比直接保存 `phone`、`real_name`、`student_number` 更安全，也便于说明隐私数据处理。

### 6.2 需要实现层配合

数据库字段命名为密文和哈希，并不自动保证安全。实现时还需要：

- 使用 BCrypt、Argon2 或 PBKDF2 存储密码哈希。
- 使用服务端密钥对手机号、姓名、学号加密。
- 使用不可逆哈希支持查重。
- 限制认证材料 `evidence_url` 的访问权限。
- 避免在普通 API 响应中返回密文字段和敏感材料地址。

## 7. 业务完整性审查

### 7.1 P0 功能覆盖情况

| P0 功能 | 数据库支持 |
|---|---|
| 用户注册 / 登录 | `users` |
| 发布需求 | `posts`、`tasks` |
| 浏览需求列表 | `posts`、`tasks` 配合统一内容查询 |
| 接单 | `task_applications`、`tasks.provider_id` |
| 查看订单详情 | `tasks`、`task_applications`、`completion_proofs`、`task_histories`、`reviews` |
| 提交评价 | `reviews` |
| 完整任务状态机 | `tasks.status`、`task_histories` |
| 双方确认 | `task_applications`、`completion_proofs`、`task_histories` |

结论：P0 功能可以被数据库设计支撑。

### 7.2 P1/P2 扩展覆盖情况

| 扩展功能 | 数据库支持 |
|---|---|
| 站内对话 / 通知 | `conversations`、`messages` |
| 附件上传 | `attachments` |
| 举报治理 | `reports`、`moderation_records` |
| 积分信用 | `user_credits`、`credit_records` |
| 用户个人主页 | `user_profiles`、`campus_verifications`、`user_credits` 聚合 |

结论：数据库设计不仅覆盖 P0，也为部分 P1/P2 功能保留了扩展空间。

## 8. 业务对象覆盖审查

数据库与主要业务对象对应关系清晰：

| 业务对象 | 数据库表 |
|---|---|
| `Post` | `posts` |
| `Reply` | `replies` |
| `PostInteraction` | `post_interactions` |
| `Task` | `tasks` |
| `TaskApplication` | `task_applications` |
| `CompletionProof` | `completion_proofs` |
| `TaskHistory` | `task_histories` |
| `Review` | `reviews` |
| `Conversation` | `conversations` |
| `Message` | `messages` |
| `Attachment` | `attachments` |
| `Report` | `reports` |
| `ModerationRecord` | `moderation_records` |

枚举值覆盖帖子分类、帖子状态、任务状态、奖励方式、会话类型、消息类型、附件归属类型等核心取值。

`tasks.category` 仅保留 `ERRAND` 与 `AGENCY`。代出勤、代签到、代课等场景不作为合法任务分类，而应作为违规内容由发布拦截、举报和管理员审核等治理机制处理。

## 9. 审查建议

### 9.1 建议保留

- 保留 `Post` 和 `Task` 分表，不合并为 `requests` 大表。
- 保留 `task_histories`，它是满足任务状态机和课程要求的关键表。
- 保留 `attachments` 的多态设计，避免为帖子、任务、消息分别建附件表。
- 保留隐私字段的密文/哈希拆分，便于说明安全设计。

### 9.2 实现约束

- 应用层必须校验多态关联的目标是否存在。
- 任务状态流转必须由服务层统一控制，不能让多个接口随意更新 `tasks.status`。
- 写入 `tasks.status` 时，应同步写入 `task_histories`。
- 提交评价时，应校验评价人是否为任务双方之一，并通过唯一索引防止重复评价。
- 附件从 `TEMPORARY` 到 `BOUND` 的绑定应校验上传者身份。

## 10. 最终结论

数据库设计整体合理，覆盖 P3 要求的 ER 图、建表 SQL、索引说明和隐私数据处理要求，能够支撑任务状态机、双方确认、双向评价、站内对话和附件上传等关键能力。

多态关联和统计字段属于工程折中，需要在实现层中明确校验策略。总体来看，该数据库设计满足 P3 阶段数据库设计提交要求。

