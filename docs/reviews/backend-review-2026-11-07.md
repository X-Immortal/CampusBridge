# CampusBridge 后端评审与实现差距

**初次评审：** 2026-11-07 · **文档复核：** 2026-11-16

## 1. 范围与基线

原代码基线为 main 提交 0f7e4fd。第 2 节记录此后工作区已有修复，覆盖认证、任务、内容、附件、会话、通知和数据库查询；第 3 节记录对照 [SRS](../srs.md) 与[架构](../architecture.md)复核发现的实现差距。

本次文档复核没有运行新一轮业务测试，也没有修改后端或前端。既有修复的测试记录与本次静态检查分别列于第 4 节；未提交的工作区不能由 HEAD 提交号独立代表。

## 2. 既有后端修复记录

| 编号 | 原问题 | 根因 | 修改位置 | 已修改行为 |
| --- | --- | --- | --- | --- |
| SEC-01 | refresh token 可以被当作 access token 使用 | JWT 只看签名和有效期，没有区分用途 | JwtUtil、JwtAuthFilter、CurrentUserResolver | access token 和 refresh token 分用途；旧 access token 保持兼容，旧 refresh token 不能访问业务接口 |
| SEC-02 | 关闭 JWT 配置后受保护接口放行 | SecurityConfig 存在旁路配置 | SecurityConfig、认证异常入口 | 受保护路径固定认证；401/403 返回 ErrorResponse |
| SEC-03 | 禁用账号或降权后旧令牌继续有效 | 过滤器只信任令牌中的旧用户信息 | JwtAuthFilter、UserMapper | 每次请求重新读取 enabled 和 role |
| SEC-04 | 上传身份可以从请求头补充 | AttachmentController 接受不可信 header | AttachmentController、AttachmentServiceImpl | 只使用安全上下文中的 uploaderId |
| AUTH-01 | 注册 code 为空导致异常；并发请求可以重复消费验证码 | 空值未先校验，删除验证码不是条件操作 | AuthServiceImpl、UserMapper | 明确返回校验错误；验证码单次条件消费 |
| AUTH-02 | 调试模式默认回显验证码，发送失败分支缺少明确控制 | 配置默认值和失败分支过宽 | EmailVerificationCodeSender、application-example.yml | 调试回显默认关闭；非调试路径投递失败拒绝继续，调试分支仍有差异，见第 3 节 |
| AUTH-03 | 更新资料覆盖 enabled、role 等账号字段 | 使用整行 User 对象回写 | UserMapper、账号服务 | 按字段更新，只修改允许的账号资料 |
| AUTH-04 | 校园认证返回 challenge hash | 直接返回数据库实体 | CampusVerificationMapper、VerificationServiceImpl | 查询只返回客户端需要的认证字段 |
| TASK-01 | 私有任务进入公共列表，游客详情看到 provider 等敏感字段 | 查询没有统一状态和脱敏边界 | TaskMapper、ContentMapper、TaskController、GuestTaskMasker | 公共查询过滤私有状态；游客列表和详情统一脱敏 |
| TASK-02 | 旧编辑请求覆盖接单或完成状态 | 先读后写，没有锁和预期状态校验 | TaskServiceImpl、TaskMapper | 编辑、重发、申请、完成和审核按任务行锁与条件更新 |
| TASK-03 | 草稿缺字段被数据库 NOT NULL 拒绝，或草稿重发绕过校验 | 草稿和正式发布共用 DDL 约束，重发只改变状态 | schema.sql、TaskServiceImpl | 草稿列可空；正式发布和重发执行完整校验 |
| TASK-04 | 完成、取消、管理员争议处理的积分规则不一致 | 多个入口各自实现副作用 | CompletionServiceImpl、TaskServiceImpl、TaskModerationService、CreditServiceImpl | 状态、历史、通知和积分在同一事务中按明确规则提交 |
| TASK-05 | 首次积分建档并发丢失更新 | 初始化和累计更新不是同一锁定路径 | UserCreditMapper、CreditServiceImpl | 唯一键初始化后锁定 credit 行再累计并写流水 |
| TASK-06 | 超时扫描跨任务持锁，状态变化后仍可能继续处理 | 候选查询和实际变更之间缺少重新检查 | TaskTimeoutServiceImpl | 每个任务独立事务，锁内重新检查 status、时间和提醒历史 |
| CONTENT-01 | 帖子删除受外键影响；回复/点赞会覆盖正文或计数 | 物理删除和整行旧快照更新 | PostServiceImpl、ReplyServiceImpl、InteractionServiceImpl | 逻辑删除，回复和计数使用专用原子更新 |
| CONTENT-02 | 采纳回复写入不合法状态值 | 把 ACCEPT_REPLY 写入仅支持 LIKE/MARK 的 post_interactions.type | InteractionServiceImpl、PostMapper | 只持久化 posts.accepted_reply_id，保持数据库枚举合法 |
| FILE-01 | 附件写入数据库不接受的 ACTIVE 状态，或被并发重复绑定 | 服务层状态名与 DDL 不一致；绑定不是 CAS | AttachmentServiceImpl、AttachmentMapper、schema.sql | 统一 TEMPORARY → BOUND，条件绑定并检查上传者、用途和 owner |
| CHAT-01 | 消息附件查询错用 conversationId；并发建私聊重复或死锁 | 查询字段错误，会话没有稳定创建/加锁顺序 | MessageMapper/XML、ConversationServiceImpl、schema.sql | 按 messageId 查询附件；双方会话固定顺序，系统会话按业务键唯一 |
| MOD-01 | 两个管理员可以重复处理举报或终态认证 | update/delete 没有预期状态条件 | ReportServiceImpl、VerificationServiceImpl、TaskModerationService | 条件更新并检查影响行数，重复处理返回冲突 |
| QUERY-01 | 内容流每类先截 500 条，深分页、总数、地点和截止排序错误 | 内存 UNION 和固定截断 | ContentMapper/XML、ContentServiceImpl | 数据库 UNION 分页/count，支持校区、地点、送达地和排序筛选 |
| CONTRACT-01 | OpenAPI 路径遗漏或与 Controller 不一致 | API 文档手工维护 | OpenApiExportTest、docs/openapi.yaml | 导出 68 条路径、83 个操作，并与实际 Spring MVC 映射比较 |

## 3. 与开发规格的差距

以下条目根据 2026-11-16 的源码静态复核登记。本次只修改文档，没有实施这些代码修复。优先级“高”表示影响权限、数据保护或核心流程，“中”表示契约、结构或操作一致性。

| 问题 | 触发条件、实现与影响 | 依据及处理方向 |
| --- | --- | --- |
| 高：私聊上下文缺少资源校验 | createConversation 只检查 POST/TASK 类型、非空 relatedId、对方启用及拉黑；不查询关联对象是否存在或双方可见。不能把关联编号当作授权证据 | [ConversationServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/ConversationServiceImpl.java)；按 SRS FR-CHAT-01 通过所属业务查询能力检查上下文 |
| 高：个人信息仍保存原值 | phone_cipher、real_name_cipher、student_number_cipher 的写入取原始输入；哈希列和 cipher 命名不能证明加密 | [AuthServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/AuthServiceImpl.java)、[VerificationServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/VerificationServiceImpl.java)；按待决事项第 3 节选定方案后实现 |
| 高：附件 URL 缺少统一私有访问 | 业务详情可按归属限制元数据，但 AliOSSUtils 返回普通对象 URL；没有应用层签名下载路径，实际字节权限依赖桶策略 | [AliOSSUtils](../../backend/src/main/java/com/campusbridge/utils/AliOSSUtils.java)；按 SRS NFR-SEC-01 同时处理元数据与文件访问 |
| 高：不完整草稿无法补全发布字段 | UpdateTaskRequest 没有 category、campus、location、deliveryLocation 等字段；保存时缺少类别/校区/地点的草稿不能通过原编号编辑再重发 | [UpdateTaskRequest](../../backend/src/main/java/com/campusbridge/dto/request/UpdateTaskRequest.java)、[TaskServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/TaskServiceImpl.java)；按 FR-TASK-02 完成草稿编辑链路 |
| 中：前端草稿仍保留旧限制 | TaskPublishView 要求字段完整才同步，草稿编辑分支提示“后端更新错误”并只存本地；恢复后发布新任务再删除原草稿，与服务端沿原编号发布不同 | [TaskPublishView](../../frontend/src/views/TaskPublishView.vue)；对齐草稿保存、编辑、发布及失败反馈，不仅修改提示文字 |
| 中：任务重复入口规则不一致 | 通用状态入口与专用履约入口对取消原因、争议起始状态及原因长度定义不同，具体兼容差异见[接口使用说明](../api-guide.md#5-任务主流程调用) | [TaskServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/TaskServiceImpl.java)、[CompletionServiceImpl](../../backend/src/main/java/com/campusbridge/service/impl/CompletionServiceImpl.java)；按待决事项第 5 节确定后共用业务校验 |
| 中：重发周期不完整 | CANCELLED 不能编辑，而 resend 不收更新字段，过期任务无法更新 deadline 后重发；旧 ACCEPTED/REJECTED 申请又受唯一键限制 | TaskServiceImpl、TaskApplicationServiceImpl；按待决事项第 4 节确定重发周期，不单独放宽数据库约束 |
| 中：截止与申请资格不一致 | 新申请只检查 OPEN，不检查 deadline；超时流程在提醒宽限结束才取消，期间可继续申请 | TaskApplicationServiceImpl、TaskTimeoutServiceImpl；按待决事项第 4 节明确时点含义后统一申请、选人和扫描 |
| 高：注册及验证码缺少完整防滥用规则 | 注册只有基础非空和重复检查，没有完整声明式长度/格式校验；注册与改邮箱验证码为内存记录，缺少发送与错误次数限制 | AuthServiceImpl、AccountController；字段规则及限制数值见待决事项第 1 节 |
| 已修复：响应契约类型和错误处理未统一 | 固定结构 Map 已替换为 DTO，Controller 使用具体 ApiResponse 泛型；登录、注册、认证和上传的失败通过 BusinessException 与统一异常处理器返回 | [ApiResponse](../../backend/src/main/java/com/campusbridge/common/ApiResponse.java)、[ErrorCode](../../backend/src/main/java/com/campusbridge/common/ErrorCode.java)、[OpenAPI](../openapi.yaml) |
| 中：模块写入权尚未落实 | BlockController 直接写 Mapper；帖子、履约、会话服务直接操作附件 Mapper；举报服务直接写任务状态 | BlockController、PostServiceImpl、CompletionServiceImpl、ConversationServiceImpl、ReportServiceImpl；按架构第 3 节经所属业务能力协作 |
| 中：邮件开关与调试语义不直观 | email.enabled=false 时，只要 mail.host 非空仍可能尝试投递；校园邮箱与注册在邮件失败时的调试回退判断不同 | [EmailVerificationCodeSender](../../backend/src/main/java/com/campusbridge/service/impl/EmailVerificationCodeSender.java)、VerificationServiceImpl；当前运行说明已写明无 SMTP 时需清空 host |
| 中：OSS 元数据失败缺少补偿 | 对象上传成功后元数据插入失败会遗留对象；帖子逻辑删除不清理附件元数据或对象 | AttachmentServiceImpl、PostServiceImpl；按待决事项第 3 节统一保留及清理策略 |

### 已核实的功能边界

- 帖子存在 PATCH 编辑接口，发帖后端只要求登录，未检查校园认证；不能写成“仅已认证用户可发帖”或“没有编辑接口”。
- 登录实际返回 token，不是 accessToken；任务列表返回 tasks，而 PagedResult 返回 items。接口使用说明按真实协议记录。
- 文件过大在 ErrorCode 中映射 HTTP 400；原文写成 413 不准确。
- 校园邮箱验证码挑战保存在 campus_verifications.evidence_url，注册及改邮箱验证码才在进程内。系统没有 challenge_hash 列。
- VerificationAutoReviewService.enqueueStudentCardReview 没有提交链路调用；不能把学生证提交后自动 OCR 描述成运行中的流程。
- 评价接受保留的 REVIEWED 状态，但不产生此状态；正常评价后仍为 COMPLETED。
- POINTS 报酬只增加接单者积分，不扣发布者余额。requiresProof 不强制上传图片。它们是现有业务语义，不应在文档修订中擅自改成支付托管或强制图片。

## 4. 验证证据与适用范围

### 4.1 2026-11-07 的执行记录

| 范围 | 当时记录的结果 | 本次复核范围 |
| --- | --- | --- |
| 后端测试 | 214 项，0 失败、错误、跳过 | 重新读取现存 33 份 Surefire XML，总数仍为 214；未重新运行测试 |
| MySQL 集成 | 33 项，任务 16、内容持久化 12、聚合查询 5 | 核对三类测试源码的连接变量、数据库保护及清理方式；未重启测试库 |
| OpenAPI | 68 条路径、83 个操作，与 MVC 映射一致 | 核对生成测试代码及 YAML；路由覆盖不代表动态载荷完整 |
| 生产打包 | JAR 构建成功，未包含测试范围 Springdoc 和测试配置 | 沿用历史记录，本轮未重新打包 |
| HTTP 验收 | 51 项请求检查通过，包含主流程及拒绝路径 | 沿用历史记录，本轮未重新发 HTTP 请求 |
| 前端 | 58 项测试通过，lint 26 条 warning、无 error，构建成功 | 沿用历史记录，本轮未执行前端测试或构建 |

上述数字是先前执行或现存报告的证据，不是本次文档修订运行的新结果，也不能证明表 3 中尚未修复的差距已经满足规格。历史验证未覆盖真实 SMTP、OSS、OCR、浏览器端到端、性能压测和远端部署。

### 4.2 2026-11-16 的文档复核

对照 JoyLink 的需求、验收、架构与公共契约文档，以及 GenSE 的需求、架构和文档组织细则，按文档用途分别整理：

- SRS 保留业务规则、状态、字段约束和验收条件；草稿、上下文权限等要求用于指导后续实现。
- 架构维护模块写入权、关系模型、核心协作和事务设计；代码未达成之处登记在第 3 节。
- API 使用说明记录当前协议和兼容差异，完整路由仍从代码导出。
- 开发运行说明维护可执行命令、配置、数据库重建与测试环境；单次结果留在本记录。
- 产品与部署取舍集中维护于待决事项；历史归档原件保持不变。

本次文档核验包含正文与源码对照、引用及编号检查、归档哈希和文字差异检查。业务代码、数据库结构及生成 OpenAPI 未在本次修订中改动。
