# CampusBridge 接口使用说明

**版本：** 1.1 · **更新日期：** 2026-11-16

[OpenAPI](openapi.yaml) 提供从 Controller 导出的路由、方法和可识别类型；本文说明公共响应、动态载荷和关键操作顺序。业务字段约束与状态条件在 [SRS](srs.md) 中维护，不另行复制全量接口清单。

## 1. 契约来源与使用方式

当前导出覆盖 68 条路径、83 个操作。生成测试会比较实际 MVC 映射与 YAML 的路径、方法集合。该比较不证明业务字段完整，也不验证每条路径的认证、输入校验和响应语义。

部分 Controller 返回 Result，其 data 为 Object；账号修改等请求使用 Map。因此 OpenAPI 中的开放对象不能解释成“任意字段均有效”。遇到动态结构，按下列入口核对：

| 内容 | 代码入口 |
| --- | --- |
| 请求 DTO、枚举 | [RequestDtos](../backend/src/main/java/com/campusbridge/dto/request/RequestDtos.java)、[ApiEnums](../backend/src/main/java/com/campusbridge/dto/enums/ApiEnums.java) |
| 成功外层 | [Result](../backend/src/main/java/com/campusbridge/pojo/Result.java) |
| 异常外层及状态码 | [ErrorResponse](../backend/src/main/java/com/campusbridge/common/ErrorResponse.java)、[ErrorCode](../backend/src/main/java/com/campusbridge/common/ErrorCode.java) |
| 登录动态载荷 | [AuthServiceImpl.login](../backend/src/main/java/com/campusbridge/service/impl/AuthServiceImpl.java) |
| 账号修改 Map 字段 | [AccountController](../backend/src/main/java/com/campusbridge/controller/AccountController.java) |
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
| 使用 ResponseDtos.PagedResult 的帖子、会话、消息、举报等接口 | items、page、size、total |
| GET /api/v1/tasks | tasks、page、size、total |
| 返回普通 List 的接口 | 数组；不能按 items 或 tasks 解包 |

普通分页空页返回空数组和对应总数。前端需要保留筛选条件，不能通过当前页长度推断全量匹配数。具体排序规则见 SRS 对应业务节。

### 3.3 异常响应与旧业务错误

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

ErrorResponse 没有 data；details 是字符串列表或 null。旧 Controller 使用 Result.error 时仍为 code/message/data/meta，且可能保留注解指定的 2xx。例如认证提交的校验失败可为 HTTP 202 配合 VERIFY_* 业务错误；附件上传失败也可能保持 HTTP 201。调用方必须同时判断 HTTP 状态和 code 是否为 SUCCESS。

| HTTP | 具体含义或代表错误码 |
| --- | --- |
| 400 | COMMON_001_VALIDATION_ERROR；FILE_001_TOO_LARGE、FILE_004_INVALID_ATTACHMENT 也映射为 400 |
| 401 | AUTH_001_UNAUTHORIZED、AUTH_002_INVALID_CREDENTIALS |
| 403 | USER_002_NOT_VERIFIED、TASK_003_PERMISSION_DENIED、CONV_003_BLOCKED、MOD_001_PERMISSION_DENIED 等 |
| 404 | TASK_001_NOT_FOUND、POST_001_NOT_FOUND、CONV_001_NOT_FOUND 等 |
| 409 | TASK_002_INVALID_STATUS_TRANSITION、TASK_007_ALREADY_APPLIED、REVIEW_001_DUPLICATE_REVIEW、COMMON_003_CONFLICT |
| 422 | CONTENT_001_BLOCKED |
| 500 | COMMON_999_INTERNAL_ERROR |

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

重发接口没有补充字段的请求体，直接验证原任务保存的数据。过期 CANCELLED 任务不能在重发请求里顺便改 deadline；现有编辑接口仅允许 DRAFT/OPEN。

## 6. 文件上传与图片消息

附件上传使用 multipart 字段 file，用途及类型使用请求头：

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Expected-Owner-Type: COMPLETION_PROOF" \
  -H "X-Attachment-Type: IMAGE" \
  -F "file=@/absolute/path/proof.png" \
  http://localhost:8081/api/v1/attachments
```

ACCESS_TOKEN 由调用者在当前终端设置。curl 的 --fail-with-body 只检查 HTTP 错误，仍须检查返回 JSON 的 code。

上传成功返回附件元数据，保存 id；完成提交使用 attachmentIds 引用。帖子上传使用 POST 用途，图片消息使用 MESSAGE 用途。类型可省略，由 Content-Type 推断；用途也可省略，但后续绑定要求与业务用途一致，调用者应明确提供。

图片消息需同时提交 type=IMAGE、content=上传结果中的 fileUrl、attachmentIds=[上传结果中的 id]。文本消息使用 TEXT 和正文，不带附件。当前没有通用附件列表或签名下载接口；业务详情返回可见附件的元数据和 URL。

## 7. 会话与已读副作用

POST /conversations 接受 relatedType、relatedId、peerUserId、可选 initialMessage。当前按用户对复用会话，relatedType 只允许 POST/TASK；关联对象存在性及双方可见性未校验，不能将传入 relatedId 当作后端已验证的资源授权。

GET /conversations/{conversationId}/messages 在分页前标记整会话已读。需要显示全局未读数时调用 /conversations/unread-count，data 含 total、system、private；读消息后应刷新相应未读展示。系统会话不可回复，不能向它发送 TEXT、IMAGE 或 SYSTEM_NOTIFICATION。

## 8. 导出和维护

从仓库根目录执行：

```bash
(cd backend && mvn -Dtest=OpenApiExportTest -DexportOpenApi=true test)
```

导出写入 docs/openapi.yaml。修改 Controller、DTO、枚举或响应类型后重新导出并检查差异。业务条件变更同步 SRS，公共结构或调用顺序变更同步本说明。完善响应 DTO 后，应优先让 OpenAPI 表达确定字段，避免长期依靠手写示例弥补类型缺失。
