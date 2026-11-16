# CI/CD 配置与运行记录

## 配置目标

P4 阶段 CI/CD 目标是让每次提交自动完成以下检查：

- 自动安装依赖
- 自动运行静态检查（后端 Checkstyle + 前端 ESLint）
- 自动运行单元测试 + 集成测试（后端 Maven Test + 前端 Node Test）
- 自动构建项目（后端打包 + 前端生产构建）
- main 分支自动部署

## Pipeline 结构

根配置文件：`.gitlab-ci.yml`

```text
root pipeline
├─ backend child pipeline: .gitlab/backend.yml    (lint → test → build → deploy)
└─ frontend child pipeline: .gitlab/frontend.yml  (lint → test → build → deploy)
```

## 敏感配置处理

`application.yml` 已加入 `.gitignore`，不会上传至 GitLab。CI 通过以下方式安全运行：

- `application-example.yml` 作为模板提交至仓库，所有敏感值使用 `${ENV_VAR:default}` 占位符
- CI 阶段执行 `cp application-example.yml application.yml` 生成运行配置
- 测试使用 `@ActiveProfiles("test")` + H2 内存数据库，无需真实 MySQL/OSS/邮件配置
- 部署时通过 GitLab CI Variables 或服务器环境变量注入真实值

## 后端流水线

配置文件：`.gitlab/backend.yml`

| Job | Stage | 镜像 | 关键命令 | 产物 |
|---|---|---|---|---|
| `backend-lint` | lint | `maven:3.9.9-eclipse-temurin-17` | `mvn checkstyle:check` | 无 |
| `backend-test` | test | `maven:3.9.9-eclipse-temurin-17` | `cp application-example.yml → application.yml` 然后 `mvn test` | `surefire-reports/` (7天) |
| `backend-build` | build | `maven:3.9.9-eclipse-temurin-17` | `cp application-example.yml → application.yml` 然后 `mvn clean package -DskipTests` | `backend/target/backend-*.jar` (1天) |
| `backend-deploy` | deploy | GitLab Runner (`campusbridge-runner`) | 复制 jar 并 `systemctl restart campusbridge` | 仅 main 分支 |

## 前端流水线

配置文件：`.gitlab/frontend.yml`

| Job | Stage | 镜像 | 关键命令 | 产物 |
|---|---|---|---|---|
| `frontend-lint` | lint | `node:20` | `npm install && npm run lint` | 无 |
| `frontend-test` | test | `node:20` | `npm install && npm test` | 无 |
| `frontend-build` | build | `node:20` | `npm install && npm run build` | `frontend/dist/` (1天) |
| `frontend-deploy` | deploy | GitLab Runner (`campusbridge-runner`) | 复制 `dist/` 到 `/opt/campusbridge/static/` | 仅 main 分支 |

---

## 流水线运行结果

### 最近一次运行概览

| 项目 | Pipeline ID | 分支 | 提交 SHA | 触发时间 | 状态 |
|---|---|---|---|---|---|
| 后端 | #296827 | main | 8f2a758c | 2026-06-05 | ✅ 全部通过 |
| 前端 | #296827 | main | 8f2a758c | 2026-06-05 | ✅ 全部通过 |

### 后端各阶段结果

| Stage | Job | 状态 | 耗时 | 关键输出 |
|---|---|---|---|---|
| lint | `backend-lint` | ✅ | 23.7 s | Checkstyle violations: **0** |
| test | `backend-test` | ✅ | 2 min 43 s | Tests run: **95**, Failures: **0**, Errors: **0**, Skipped: **0** |
| build | `backend-build` | ✅ | 2 min 36 s | `backend-0.0.1-SNAPSHOT.jar` 打包成功 |
| deploy | `backend-deploy` | ✅ | — | 复制 jar 并重启 campusbridge 服务（main 分支） |

### 前端各阶段结果

| Stage | Job | 状态 | 耗时 | 关键输出 |
|---|---|---|---|---|
| lint | `frontend-lint` | ✅ | — | ESLint: **0** errors, **0** warnings |
| test | `frontend-test` | ✅ | 86.9 s | Tests: **40** passed / **0** failed |
| build | `frontend-build` | ✅ | 2.81 s | `dist/` 产出：CSS 171.7 kB + JS 379.0 kB |
| deploy | `frontend-deploy` | ✅ | — | 复制 `dist/` 到 `/opt/campusbridge/static/`（main 分支） |

### 后端测试详情

| 测试类 | 测试数 | 通过 | 失败 | 错误 |
|---|---|---|---|---|
| CampusBridgeApplicationTests | 3 | 3 | 0 | 0 |
| AuthControllerIntegrationTest | 4 | 4 | 0 | 0 |
| TaskWorkflowIntegrationTest | 4 | 4 | 0 | 0 |
| BE01AuthServiceImplTest | 13 | 13 | 0 | 0 |
| BE03UserProfileServiceImplTest | 5 | 5 | 0 | 0 |
| BE04BE08TaskServiceImplTest | 7 | 7 | 0 | 0 |
| BE07TaskApplicationServiceImplTest | 10 | 10 | 0 | 0 |
| BE09CompletionServiceImplTest | 12 | 12 | 0 | 0 |
| BE10ContentModerationServiceImplTest | 11 | 11 | 0 | 0 |
| BE11GlobalExceptionHandlerTest | 2 | 2 | 0 | 0 |
| BE11TaskHistoryServiceImplTest | 2 | 2 | 0 | 0 |
| BE13ConversationReadServiceImplTest | 5 | 5 | 0 | 0 |
| BE14TaskTimeoutServiceImplTest | 3 | 3 | 0 | 0 |
| BE16CreditServiceImplTest | 1 | 1 | 0 | 0 |
| BE16ReportServiceImplTest | 3 | 3 | 0 | 0 |
| BE16ReviewServiceImplTest | 10 | 10 | 0 | 0 |
| **合计** | **95** | **95** | **0** | **0** |

### 前端测试详情

| 测试文件 | 测试数 | 通过 | 失败 |
|---|---|---|---|
| `task-plaza-filter.test.mjs` | — | ✅ | 0 |
| `task-applications.test.mjs` | — | ✅ | 0 |
| `task-completion.test.mjs` | — | ✅ | 0 |
| `task-publish.test.mjs` | — | ✅ | 0 |
| `my-tasks.test.mjs` | — | ✅ | 0 |
| `admin-workflows.test.mjs` | — | ✅ | 0 |
| **合计** | **40** | **40** | **0** |

---

## 本地验证记录

| 时间 | 命令 | 结果 |
|---|---|---|
| 2026-06-05 | `cd backend && mvn checkstyle:check` | BUILD SUCCESS，0 violations |
| 2026-06-05 | `cd backend && mvn test` | BUILD SUCCESS，95 tests, 0 failures |
| 2026-06-05 | `cd frontend && npm run lint` | 通过，0 errors |
| 2026-06-05 | `cd frontend && npm test` | 40 passed / 0 failed |
| 2026-06-05 | `cd frontend && npm run build` | built in 2.81s |

---

## 部署说明

`backend-deploy` 与 `frontend-deploy` 仅在 `main` 分支触发，且依赖带有 `campusbridge-runner` 标签的 GitLab Runner：

- 后端 jar 部署到 `/opt/campusbridge/backend.jar`
- 前端静态资源部署到 `/opt/campusbridge/static/`
- 后端服务通过 `systemctl stop/start campusbridge` 重启

---

## 环境变量对照表

需要在 GitLab Settings → CI/CD → Variables 中配置（部署到服务器时需要，仅测试不需要）：

| 变量名 | 用途 | 示例值 |
|---|---|---|
| `MYSQL_URL` | 数据库连接地址 | `jdbc:mysql://localhost:3306/campusbridge?...` |
| `MYSQL_USERNAME` | 数据库用户名 | `root` |
| `MYSQL_PASSWORD` | 数据库密码 | `******` |
| `SMTP_HOST` | SMTP 服务器 | `smtp.163.com` |
| `SMTP_PORT` | SMTP 端口 | `465` |
| `SMTP_USERNAME` | 邮箱账号 | `xxx@163.com` |
| `SMTP_PASSWORD` | 邮箱密码/授权码 | `******` |
| `ALIYUN_OSS_ENDPOINT` | OSS Endpoint | `https://oss-cn-beijing.aliyuncs.com` |
| `ALIYUN_OSS_BUCKET` | OSS Bucket | `campusbridge` |
| `ALIYUN_OSS_REGION` | OSS Region | `cn-beijing` |
| `JWT_SECRET` | JWT 签名密钥 | `至少64字符的随机字符串` |
| `VERIFICATION_EMAIL_CODE_SECRET` | 邮件验证码加密密钥 | 随机字符串 |
