# API 规范说明

## 1. 文件说明

本目录提交的 API 规范包含以下文件：

| 文件 | 说明 |
|---|---|
| `openapi.yaml` | OpenAPI 3.0.3 格式的正式 API 规范文件 |
| `API规范文档.html` | 由 `openapi.yaml` 渲染生成的可读版 API 文档 |
| `openapi-ai初稿.yaml` | AI 生成的 API 初稿，用于 P3 任务 2.2 对比实验 |
| `API设计AI辅助实验.md` | API 初稿审查与修正说明 |

其中，`openapi.yaml` 是本阶段 API 设计的主要提交物；`API规范文档.html` 用于浏览器查看，便于按模块检查接口路径、请求参数、响应结构和错误响应。

## 2. 覆盖范围

API 规范覆盖 P3 要求的核心接口：

| P3 要求 | 对应接口 |
|---|---|
| 用户注册 / 登录 | `POST /auth/register`、`POST /auth/login` |
| 发布需求 | `POST /posts`、`POST /tasks` |
| 浏览需求列表（含筛选） | `GET /content`、`GET /posts`、`GET /tasks` |
| 接单 | `POST /tasks/{taskId}/applications`、`POST /tasks/{taskId}/applications/{applicationId}/accept` |
| 查看订单详情 | `GET /tasks/{taskId}` |
| 提交评价 | `POST /tasks/{taskId}/reviews` |

除上述 P0 核心接口外，规范还补充了用户主页、校内认证、帖子回复、点赞标记、采纳回复、任务状态流转、完成凭证、站内会话、附件上传、举报申诉和管理员审核等接口。

## 3. 规范内容

`openapi.yaml` 对每个接口给出了：

- URL 路径与 HTTP 方法；
- 请求参数、请求体 schema、字段类型和必要校验；
- 成功响应结构；
- 常见失败响应；
- 统一错误码与错误响应格式；
- Bearer JWT 认证配置。

接口整体采用 RESTful 风格，以资源路径组织用户、帖子、任务、会话、附件、举报等对象，并使用 `GET`、`POST`、`PATCH`、`DELETE` 表达查询、创建、修改和删除/取消等操作。

## 4. 校验说明

`openapi.yaml` 已使用 Redocly 进行校验，结果为 API 描述文件有效。校验中出现的 warning 主要涉及 `operationId`、tag 描述、license 字段等推荐项，不影响 OpenAPI 文件的有效性，也不影响 P3 要求的接口规范覆盖。
