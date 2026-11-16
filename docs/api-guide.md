# CampusBridge 接口使用说明

**版本：** 1.2 · **更新日期：** 2026-11-16

[OpenAPI](openapi.yaml) 提供从 Controller 导出的路由、方法和可识别类型；本文说明公共响应、业务载荷和关键操作顺序。业务字段约束与状态条件在 [SRS](srs.md) 中维护，不另行复制全量接口清单。

## 1. 契约来源与使用方式

当前导出覆盖 68 条路径、83 个操作。生成测试会比较实际 MVC 映射与 YAML 的路径、方法集合。该比较不证明业务字段完整，也不验证每条路径的认证、输入校验和响应语义。

Controller 使用 `ApiResponse<T>` 声明具体响应类型。请求字段由请求 DTO 定义，成功载荷由响应 DTO、视图或实体定义，分页保留元素类型。接口调用入口如下：

| 内容 | 代码入口 |
| --- | --- |
| 请求 DTO、枚举 | [请求 DTO](../backend/src/main/java/com/campusbridge/dto/request/)、[枚举](../backend/src/main/java/com/campusbridge/enums/) |
| 成功外层 | [ApiResponse](../backend/src/main/java/com/campusbridge/common/ApiResponse.java) |
| 异常外层及状态码 | [ErrorResponse](../backend/src/main/java/com/campusbridge/common/ErrorResponse.java)、[ErrorCode](../backend/src/main/java/com/campusbridge/common/ErrorCode.java) |
| 登录响应字段 | [AuthServiceImpl.login](../backend/src/main/java/com/campusbridge/service/impl/AuthServiceImpl.java) |
| 账号修改请求字段 | [AccountController](../backend/src/main/java/com/campusbridge/controller/AccountController.java) |
| 任务分页及动作 | [TaskController](../backend/src/main/java/com/campusbridge/controller/TaskController.java)、[TaskServiceImpl](../backend/src/main/java/com/campusbridge/service/impl/TaskServiceImpl.java) |
| 前端现有调用 | [api.js](../frontend/src/services/api.js) |

## 2. 认证与请求格式

JSON 业务接口使用 /api/v1 前缀。除公开入口外，请求携带：

```http
Authorization: Bearer <登录结果中的 token>
Content-Type: application/json
```

POST /api/v1/auth/login 接受 username、password。成功 data 的实际字段如下，令牌文本为示意占位：

```json
{
  "userId": 101,
  "username": "demo_requester",
  "role": "USER",
  "token": "<access-token>",
  "refreshToken": "<refresh-token>"
}
```

访问令牌字段名是 token。刷新令牌不能调用普通业务接口，当前没有换取新访问令牌的 endpoint。禁用账号或无效令牌访问受限资源返回 401；ADMIN 权限由当前账号角色判断。X-User-Id 即使仍在旧客户端发送，也不能作为身份凭据。

时间使用不带时区偏移的 LocalDateTime 文本，例如 2026-11-17T18:30:00；前后端及数据库运行环境需保持一致的业务时间配置。

## 3. 响应、分页和错误

### 3.1 成功响应

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": null,
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-11-16T12:00:00"
  }
}
```

data 由具体接口决定，可以是编号、对象、数组、分页对象或 null。注册成功 data 为用户编号；登录为上一节的对象。meta.requestId 在响应对象创建时生成，当前不能据此承诺已与每条服务端日志贯通。

### 3.2 分页差异

常用列表默认 page=1、size=20，上限 100，但不是所有列表都分页。响应必须按接口读取：

| 返回来源 | data 结构 |
| --- | --- |
| 使用 PagedResult 的帖子、会话、消息、举报等接口 | items、page、size、total |
| GET /api/v1/tasks | tasks、page、size、total |
| 返回普通 List 的接口 | 数组；不能按 items 或 tasks 解包 |

普通分页空页返回空数组和对应总数。前端需要保留筛选条件，不能通过当前页长度推断全量匹配数。具体排序规则见 SRS 对应业务节。

### 3.3 异常响应

BusinessException 和已登记的 MVC 错误由 GlobalExceptionHandler 转换：

```json
{
  "code": "TASK_002_INVALID_STATUS_TRANSITION",
  "message": "任务状态流转不合法",
  "details": null,
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440001",
    "timestamp": "2026-11-16T12:00:00"
  }
}
```

ErrorResponse 没有 data；details 是字符串列表或 null。业务失败由 Service 抛出 BusinessException，统一异常处理器按 ErrorCode 返回非 2xx 状态。认证和附件上传的失败同样使用此结构；前端公共请求函数根据 HTTP 状态、code 和 message 展示错误。

| HTTP | 具体含义或代表错误码 |
| --- | --- |
| 400 | COMMON_001_VALIDATION_ERROR、ATTACH_001_INVALID_INPUT、ATTACH_002_INVALID_OWNER_TYPE、VERIFY_001_INVALID_INPUT、VERIFY_007_EMAIL_CODE_EXPIRED、VERIFY_008_EMAIL_CODE_MISMATCH；文件大小和附件校验失败也映射为 400 |
| 401 | AUTH_001_UNAUTHORIZED、AUTH_002_INVALID_CREDENTIALS |
| 403 | USER_002_NOT_VERIFIED、TASK_003_PERMISSION_DENIED、CONV_003_BLOCKED、MOD_001_PERMISSION_DENIED 等 |
| 404 | TASK_001_NOT_FOUND、POST_001_NOT_FOUND、CONV_001_NOT_FOUND、VERIFY_004_NOT_FOUND 等 |
| 409 | TASK_002_INVALID_STATUS_TRANSITION、TASK_007_ALREADY_APPLIED、REVIEW_001_DUPLICATE_REVIEW、COMMON_003_CONFLICT、VERIFY_002_ALREADY_PENDING_OR_VERIFIED、VERIFY_006_EMAIL_CODE_NOT_PENDING、VERIFY_009_CANNOT_CANCEL |
| 422 | CONTENT_001_BLOCKED |
| 500 | COMMON_999_INTERNAL_ERROR、ATTACH_005_SAVE_FAILED、VERIFY_003_SUBMIT_FAILED |
| 502 | ATTACH_004_UPLOAD_FAILED、VERIFY_005_EMAIL_SEND_FAILED |

应用对上传过大异常当前返回 400，不是 413；入口代理另行拒绝请求时可能使用自己的状态及响应体。错误码枚举中存在某个值不等于当前业务路径一定会触发它，例如验证码限流尚未实现。

## 4. 注册、账号邮箱与校园邮箱

这三类验证码用途独立：

| 流程 | 调用顺序 | 结果 |
| --- | --- | --- |
| 注册 | POST /auth/register/send-code，body 为 email；收到验证码后 POST /auth/register，提供 username/password/phone/email/code | 返回新用户编号，再登录取得 token |
| 修改账号邮箱 | PUT /account/email，提供 email；PUT /account/email/confirm，提供 code | 修改本人 users 邮箱 |
| 校园邮箱认证 | POST /verifications，提供 CAMPUS_EMAIL 和实名、学校、campusEmail；POST /verifications/email/confirm，提供 code | 当前校园认证从 PENDING 变 VERIFIED |

表中路径均相对于 /api/v1。校园邮箱待审验证码可通过 POST /verifications/email/code 重发。认证结果须读 data.status，HTTP 202 只说明请求按入口语义返回，不能当成已通过认证。

学生证或人工认证提交提供 evidenceUrl，进入待审；该 URL 不等同于通过 attachmentIds 完成业务绑定。当前提交路径不触发 OCR 自动审核。

帖子编辑使用 PUT /api/v1/posts/{postId}，完整提交 title 和 content；两者均非空，title 最长 100 字符。status 可选，提供时按作者可执行的状态变更规则处理，省略时保留原状态。请求字段校验由入口完成，业务服务检查归属、状态权限和内容审核后保存。

输入校验统一在 Controller 执行：请求 DTO 使用 Validation 约束，草稿保存与正式发布分别使用独立请求 DTO，取消原因的查询参数最多 300 字符。校园认证的字段及认证方式相关输入约束失败时返回 VERIFY_001_INVALID_INPUT，其余普通输入校验失败返回 COMMON_001_VALIDATION_ERROR；错误详情放在 details 中。

## 5. 任务主流程调用

调用前准备两个登录用户，发布者须 VERIFIED。以下仅列主流程操作，完整路径和请求形状查 OpenAPI。

| 顺序 | 操作 | 必须保留的结果 |
| --- | --- | --- |
| 1 | POST /tasks 提交符合 SRS 第 5 节的任务字段 | data.id，状态 OPEN |
| 2 | 另一用户 POST /tasks/{taskId}/applications，提供 message | applicationId |
| 3 | 发布者 POST /tasks/{taskId}/applications/{applicationId}/accept | 被选 provider 和 ACCEPTED 状态 |
| 4 | 接单者 POST /tasks/{taskId}/status-transitions，body 为 action=START | IN_PROGRESS |
| 5 | 接单者 POST /tasks/{taskId}/completion，提供 description、可选 attachmentIds | 完成凭证；任务进入 PENDING_CONFIRMATION |
| 6 | 发布者 POST /tasks/{taskId}/completion/confirm | 查询详情确认 COMPLETED、积分和历史 |
| 7 | 双方分别 POST /tasks/{taskId}/reviews，提供另一方 revieweeId、rating、可选 comment | 各自唯一评价 |

正式任务示例，deadline 必须替换为执行时的未来时间：

```json
{
  "category": "ERRAND",
  "title": "帮忙取一份打印材料",
  "description": "到校内打印店领取已装订的课程材料。",
  "campus": "仙林校区",
  "location": "校内打印店",
  "deliveryLocation": "图书馆门口",
  "deadline": "<未来的本地时间，如 YYYY-MM-DDTHH:mm:ss>",
  "rewardType": "POINTS",
  "rewardAmount": 5,
  "completionStandard": "材料完整交付并在站内确认。",
  "requiresProof": false,
  "onlyInAppContact": true,
  "attachmentIds": []
}
```

[SRS 第 6 节](srs.md#6-履约取消争议和超时)要求相同业务动作遵循统一规则；现有协议的以下差异在后续对齐前仍需由调用方识别：

| 动作 | 通用 status-transitions | 专用入口 |
| --- | --- | --- |
| 取消 | CANCEL 在 OPEN 也要求非空原因，最多 300 字符 | DELETE 任务在 OPEN 可省略原因，其余可取消状态必填，最多 300 字符 |
| 驳回 | REJECT_COMPLETION 原因必填，最多 300 字符 | completion/reject 原因必填，最多 500 字符 |
| 争议 | DISPUTE 允许 ACCEPTED / IN_PROGRESS / PENDING_CONFIRMATION，原因最多 300 字符 | completion/dispute 允许 IN_PROGRESS / PENDING_CONFIRMATION，原因最多 500 字符 |

重复确认返回冲突，客户端应重新读取当前状态。

任务详情的 history 数组使用 eventType 标识历史事件，取值见 OpenAPI 的 TaskHistory 定义；fromStatus、toStatus 保留动作前后状态。note 是可选的补充说明或操作原因，可为 null，客户端按 eventType 映射展示名称，并单独显示 note。

PUT /api/v1/tasks/{taskId} 完整替换任务的可编辑字段。请求必须提供 title、description、deadline、rewardType、completionStandard、requiresProof、onlyInAppContact、allowOfflineContact；rewardDescription 和 rewardAmount 为可选值，传 null 或省略均表示清除，不保留旧值。category、campus、location、发布者及任务状态等不属于此接口的可编辑字段。服务端检查任务归属及 DRAFT/OPEN 状态，OPEN 任务的更新内容须通过内容审核。草稿保存使用 POST /api/v1/tasks/drafts，允许字段未填完整。

POST /api/v1/tasks/{taskId}/resend 接受与正式创建任务相同的完整发布请求。DRAFT 或 CANCELLED 的发布者可提交修订后的内容和未来截止时间；服务端重新检查校园认证、请求约束、奖励及内容审核，在同一事务中保存新内容、清空接单者、将状态设为 OPEN 并记录历史。任务编号、发布者和原创建时间保留，可选字段省略或传 null 时按本次表单保存。草稿恢复发布也使用此接口。

## 6. 图片上传与图片消息

附件仅支持 PNG、JPEG、WebP 图片，使用 multipart 字段 file，用途使用请求头：

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Expected-Owner-Type: COMPLETION_PROOF" \
  -F "file=@/absolute/path/proof.png" \
  http://localhost:8080/api/v1/attachments
```

ACCESS_TOKEN 由调用者在当前终端设置。curl 的 --fail-with-body 只检查 HTTP 错误，仍须检查返回 JSON 的 code。

上传成功返回附件元数据，保存 id；完成提交使用 attachmentIds 引用。帖子上传使用 POST 用途，图片消息使用 MESSAGE 用途。附件元数据保留 contentType，不另设 type 分类。用途可省略，但后续绑定要求与业务用途一致，调用者应明确提供。

图片消息需同时提交 type=IMAGE、content=上传结果中的 fileUrl、attachmentIds=[上传结果中的 id]。文本消息使用 TEXT 和正文，不带附件。当前没有通用附件列表或签名下载接口；业务详情返回可见附件的元数据和 URL。

## 7. 私聊、通知与已读

POST /conversations 接受 relatedType、relatedId、peerUserId、可选 initialMessage。当前按用户对复用会话，relatedType 只允许 POST/TASK；关联对象存在性及双方可见性未校验，不能将传入 relatedId 当作后端已验证的资源授权。

GET /conversations/{conversationId}/messages 在分页前标记整会话已读。GET /conversations/unread-count 返回 data.unreadCount，表示当前用户全部私聊未读消息数。消息类型为 TEXT 或 IMAGE。

站内通知使用独立接口，下表路径均以 /api/v1 为前缀。

| 方法与路径 | 请求与响应 |
| --- | --- |
| GET /notifications | page 默认 1，size 默认 20、上限 100；可传 types（NotificationType 编码逗号分隔）、unreadOnly 和 keyword。返回 items/page/size/total，按 createdAt、id 倒序 |
| GET /notifications/unread-count | 返回 data.unreadCount，统计本人所有未读通知 |
| POST /notifications/{notificationId}/read | 返回该通知的当前数据；重复操作保持首次 readAt；非本人或不存在的记录返回 NOTICE_001_NOT_FOUND |
| POST /notifications/read-all | 标记本人全部未读通知，返回 data.updatedCount；不受列表分页或筛选限制 |

NotificationView 返回 id、type、title、content、relatedType、relatedId、readAt、createdAt。readAt 为 null 表示未读。relatedType 为 TASK 或 POST，relatedId 对应业务对象编号。type 表示具体通知事件，枚举值以 OpenAPI 为准；客户端按类型决定分类和样式，直接显示 title/content，并根据关联对象跳转详情。列表查询不改变已读状态，已读操作不影响私聊。导航总未读数为两个 unread-count 接口结果之和。

## 8. 内容查询、采纳回复与统计

GET /api/v1/content 返回混合内容列表。itemType 区分 POST 和 TASK，sourceId 为对应业务编号；category 和 status 返回该业务类型的编码。任务奖励分别通过 rewardType、rewardAmount、rewardDescription 返回，帖子这三个字段为 null。客户端负责将编码映射为显示文案并组织奖励说明。

POST /api/v1/posts/{postId}/replies/{replyId}/accept 成功后，data 返回 postId 和 acceptedReplyId，表示该帖子当前采纳的回复。

后台任务状态、举报状态及举报对象分布接口返回 code/count 列表，仅包含查询所得的分组计数。前端分别使用任务状态、举报状态和举报对象选项生成标签、补齐零计数项及排序。

举报裁决请求使用 ReportStatus 编码，只允许 APPROVED、REJECTED、RESOLVED、PUNISHED 四种终态；PENDING 和 PROCESSING 不能作为裁决结果。

内容列表的 itemTypes 使用 POST、TASK，多个类型以逗号分隔；rewardType 使用奖励类型枚举；sortBy 使用 CREATED_AT、DEADLINE。非法枚举编码返回 HTTP 400。

任务列表的 sortBy 使用 CREATED_AT_DESC、DEADLINE_ASC、DEADLINE_DESC。举报查询 status、会话 relatedType、附件 X-Expected-Owner-Type 均按 OpenAPI 中的大写枚举编码传入；非法编码返回 HTTP 400。附件用途可省略；上传服务检查图片 Content-Type，非支持格式返回 FILE_002_UNSUPPORTED_TYPE。

## 9. 导出和维护

从仓库根目录执行：

```bash
(cd backend && mvn -Dtest=OpenApiExportTest -DexportOpenApi=true test)
```

导出写入 docs/openapi.yaml。修改 Controller、DTO、枚举或响应类型后重新导出并检查差异。业务条件变更同步 SRS，公共结构或调用顺序变更同步本说明。完善响应 DTO 后，应优先让 OpenAPI 表达确定字段，避免长期依靠手写示例弥补类型缺失。
