# M4 数据治理与个人记忆

## 数据目录

需求方、服务方是任务中的参与身份。应用账号角色以 `users.role` 为准（`USER`、`ADMIN`）。

| 真表 | 关键字段与用途 | 更新时机 | 维护责任 | 敏感性 |
|---|---|---|---|---|
| [users](../deploy/mysql/schema.sql#L5) | `id` 用于关联业务主体，`role` 用于权限判断，`password_hash`、`phone_cipher` 为账号凭据和隐私数据 | 注册、账号更新 | 账号模块 | 受限 |
| [campus_verifications](../deploy/mysql/schema.sql#L41) | `user_id`、`status` 表示认证状态，`reviewer_id` 记录审核人，`real_name_cipher`、`student_number_cipher` 保存加密身份资料 | 申请、审核 | 认证模块 | 受限 |
| [tasks](../deploy/mysql/schema.sql#L142) | `id`、`requester_id`、`provider_id` 标识任务和参与方，`title`、`status`、`deadline`、`reward_type` 支持展示与状态校验 | 建单、状态变化 | 任务模块 | 公开投影与内部字段分开 |
| [task_histories](../deploy/mysql/schema.sql#L206) | `task_id`、`operator_id`、`from_status`、`to_status` 支持状态追踪 | 状态变化 | 任务模块 | 内部 |
| [conversations](../deploy/mysql/schema.sql#L239) | `owner_id`、`peer_user_id` 表示会话视角，`related_type`、`related_id` 关联任务 | 创建会话、交互 | 消息模块 | 私密 |
| [messages](../deploy/mysql/schema.sql#L269) | `conversation_id`、`sender_id`、`content` 记录站内沟通 | 发信 | 消息模块 | 私密 |
| [reports](../deploy/mysql/schema.sql#L303)、[moderation_records](../deploy/mysql/schema.sql#L319) | `reporter_id`、`target_type`、`target_id` 定位举报，`report_id`、`handler_id`、`status` 追踪处理 | 举报、审核 | 治理模块 | 受限 |
| [ai_event](../backend/src/main/java/com/campusbridge/events/AiSchemaInitializer.java#L28) | `seq`、`event_type`、`trace_id`、`session_id`、`payload`、`created_at` 支持助手链路诊断 | 助手及网关记录事件时 | 事件模块 | 受限，载荷按事件审查 |

任务、身份、消息和审核数据使用各自的业务接口。评测样本来自 `evals/cases/`，个人记忆当前保存在服务实例内存中，实例重启后清空。持久化事件和业务资料的保留期限待项目确认。

## 质量规则

[governance.sql](../deploy/mysql/governance.sql) 为只读聚合审计，输出规则标识及异常行数。先初始化 demo 的 H2 MySQL 兼容模式数据库并完成 `AiSchemaInitializer`，再在同一 JDBC 实例执行。MySQL 先初始化对应业务表并启动后端建 `ai_event`，然后在 demo schema 执行 `SOURCE deploy/mysql/governance.sql`。

| 规则 | 数据源与判定 | 异常处理 |
|---|---|---|
| 必填 | `tasks` 发布相关字段存在，标题和完成标准非空白 | 检查任务写入和导入路径 |
| 枚举 | `tasks.category/reward_type/status` 匹配 `ApiEnums` | 核查写入值与枚举兼容性 |
| 引用完整性 | `tasks`、`task_histories`、`campus_verifications` 的关联键必须指向已存在的记录，可空字段按表定义处理 | 按任务或用户来源定位孤儿行 |
| 链路标识 | `ai_event.trace_id/session_id` 非空白 | 通过事件类型定位写入方 |
| 确认与幂等 | 核对 `ai_event` 中的确认、重放、工具结果和 `tasks` 的实际写入。一次有效确认对应一次业务创建 | 交由助手与工具集成负责人复核 |
| 版本归因 | `ai_event.payload` 的模型、本体、提示词、Schema 和工具版本字段由事件负责人统一补齐 | 缺失时标记该条链路无法完成版本归因 |

已在 H2 MySQL 模式与独立 MySQL 9.4.0 实例上验收。MySQL 加载项目正式建表脚本、演示种子数据与 AI 事件 DDL，含 50 条演示任务。两种环境的七项异常计数均为 0。加入一条缺少链路标识的合成事件后，只有 event_trace 和 event_session 各变为 1。

## 权限矩阵

以下是应用层的数据访问边界。导出由项目另行审批，SQL 审计只输出计数。

| 身份 | `users` / `campus_verifications` | `tasks` / `task_histories` | `conversations` / `messages` | `reports` / `moderation_records` | `ai_event` |
|---|---|---|---|---|---|
| 需求方 | 自己的账号及认证 | 读本人任务，按状态规则创建和变更 | 本人参与的会话读写 | 提交举报，查看本人有权查看的结果 | 仅会话协议提供给本人的结果 |
| 服务方 | 自己的账号及认证 | 读可见任务，按授权执行参与动作 | 本人参与的会话读写 | 同普通用户 | 同普通用户 |
| 第三人 | 公开资料投影 | 读公开任务投影 | 无定向邀请沟通访问权 | 提交本人举报 | 无直接访问权 |
| 管理员 | 通过审核接口读取必要资料 | 经管理权限读取和处置 | 仅经授权的治理流程访问 | 审核与处置 | 经授权排查 |
| 模型服务 | 仅经后端筛选的必要上下文 | 使用后端构造的最小业务上下文 | 无表访问权 | 无表访问权 | 无表访问权 |

## 1.0 到 2.0 的血缘

- `users.id → tasks.requester_id`。已认证主体进入助手会话，经确认进入工具层，再通过原任务服务写业务表。
- `tasks.id → task_histories.task_id`。原任务服务维护业务状态事实。助手建单成功后记录 `ai_event.event_type = 'task.created'`，可用事件载荷中的 `taskId` 对回业务行。
- `ai_event.trace_id/session_id` 关联会话、澄清、模型降级和工具阶段。卡片来自助手当前会话草稿。确认发生在建单之前，建单结果再向会话返回任务 ID。
- `conversations.related_id → tasks.id`（`related_type = 'TASK'`），`messages.conversation_id → conversations.id`。这条沟通链用于 1.0 任务协作，访问以会话参与关系为准。

## 交互全过程事件（16 类）

助手链路的业务事件由 `EventRecorder` 统一写内存环和 `ai_event` 表；每条 payload 由 `VersionInfo` 补
`modelVersion / ontologyVersion / promptVersion / schemaVersion / toolVersion`。口径是 **16 个业务事件 + `model.*` 网关事件 + 5 种流事件**。

| # | 课件阶段 | 事件名 | 触发点 |
|---|---|---|---|
| 1 | 目标表达 | `session.started` | `AgentOrchestrator.start`，带 `requesterId/goalLength/memoryCount` |
| 2 | 意图假设 | `understanding.applied` | 应用模型理解后，带 `intent/confidence/slotCount/declaredCount` |
| 3 | 澄清 | `clarify.asked` | `advance`，带待补字段列表 |
| 4 | 纠正/回答 | `clarify.answered` | `answer`，逐字段记录 |
| 5 | 无效回答 | `answer.rejected` | 字段不在受控词汇表时 |
| 6 | 确认卡生成 | `confirmation.issued` | `select`，带 `option/taskVersion/paramDigest` |
| 7 | 卡片操作 | `confirm.requested` | `confirm` 入口，带 `taskVersion` |
| 8 | 重复确认 | `confirm.replay` | 已建单会话再次确认 |
| 9 | 规则/Schema 拒绝 | `policy.rejected` | `ToolGateway`，带违规 `rules` |
| 10 | 工具请求 | `tool.request` | `ToolGateway.execute` |
| 11 | 工具拒绝 | `tool.denied` | 令牌/策略拒绝 |
| 12 | 工具失败 | `tool.failure` | 业务端口异常 |
| 13 | 工具成功 | `tool.success` | 建单成功，带 `taskId` |
| 14 | 幂等补偿 | `tool.idempotent.replay` | 同一 `idempotencyKey` 重放 |
| 15 | 任务创建 | `task.created` | 确认成功，带 `taskId/replay/memoryWritten` |
| 16 | 放弃/撤销 | `session.cancelled` | 用户取消会话 |

**边界说明**：任务完成由 1.0 的完成接口维护，助手不重复记 `task.completed`；人工接管本版本未实现，答辩时按“不做”说明。Schema 生成走流事件 `schema.card`，渲染在客户端白名单校验之后，拒绝走 `policy.rejected` / `answer.rejected`。`model.*` 网关事件见 `docs/M3-slo-loadtest.md` 第四节。

## 个人记忆

| 类型 | 创建条件 | 读取、修改与撤回 |
|---|---|---|
| `DECLARED` | 用户明确声明，服务存储原值 | 按所有者读取、改值、删除 |
| `INFERRED` | 类型固定派生 `inferred=true`，随对象序列化 | 按所有者读取、改值、删除，集成界面应显示“推测偏好” |
| `TEMPORARY` | 必须提供未来的 `expiresAt` | 到期时不可读或撤回，访问时及每分钟清理 |

集成时通过启动参数 `--memory.http.enabled=true` 启用 HTTP，默认关闭。现有 Spring Security 默认认证规则适用于这些端点。所有者由 JWT 认证主体决定，请求携带的 `userId` 必须与其一致。

| 端点 | 请求及结果 |
|---|---|
| `GET /api/memory?userId=` | `userId` 可省略，成功返回当前用户的记忆列表 |
| `POST /api/memory` | JSON 包含 `type`、`value`，可携带 `userId`。临时记忆须有未来的 ISO 8601 `expiresAt`，创建成功返回 201 及记忆对象 |
| `PUT /api/memory/{id}` | 所有者修改 `value`；临时记忆可同时续 `expiresAt`，声明/推测记忆不接受过期时间 |
| `DELETE /api/memory/{id}` | 所有者撤回时返回 204。ID 不存在或归属他人时返回 404 |

个人记忆只按所有者读写，不进入任务列表/详情等公开投影，也不在未认证接口暴露；`userId` 参数必须与 JWT 主体一致。

助手链路已经接入个人记忆：会话开始时读取当前用户的记忆并在响应 `notes` 里召回（例如“我记得你之前说过：希望以后联系同一个人”）；目标或回答里出现“以后联系同一个人”时，比较卡会多出“只找上次那位”选项，确认建单后写入一条 `DECLARED` 记忆，重复确认不会重复写。这样 M4 现场挑战里的“希望以后还能联系同一个人”不再被丢掉。

记忆存储 6 项、HTTP 集成 4 项与 Agent 记忆流程 1 项均通过。HTTP 集成覆盖匿名请求拒绝、所有者读写、跨账号拒绝、临时到期必填与 PUT 修改；Agent 流程覆盖“写入—重复确认不重复写—新会话召回”。后端定向验收命令为 `cd backend; mvn -o -q "-Dtest=AgentFlowTest,MemoryStoreTest,MemoryControllerIntegrationTest" test`。
