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

> **填写说明：** 以下内容需在 GitLab CI 实际运行后填写。

### 最近一次运行概览

| 项目 | Pipeline ID | 分支 | 提交 SHA | 触发时间 | 总耗时 | 状态 |
|---|---|---|---|---|---|---|
| 后端 | #_____ | | | | | ✅ / ❌ |
| 前端 | #_____ | | | | | ✅ / ❌ |

### 后端各阶段结果

| Stage | Job | 状态 | 耗时 | 关键输出 |
|---|---|---|---|---|
| lint | `backend-lint` | | | Checkstyle violations: _____ |
| test | `backend-test` | | | Tests run: _____, Failures: _____, Errors: _____ |
| build | `backend-build` | | | JAR 大小: _____ |
| deploy | `backend-deploy` | | | 仅 main 分支触发 |

### 前端各阶段结果

| Stage | Job | 状态 | 耗时 | 关键输出 |
|---|---|---|---|---|
| lint | `frontend-lint` | | | ESLint: _____ errors, _____ warnings |
| test | `frontend-test` | | | Tests: _____ passed / _____ failed |
| build | `frontend-build` | | | Build time: _____, dist 大小: _____ |
| deploy | `frontend-deploy` | | | 仅 main 分支触发 |

### 后端测试详情（surefire-reports 摘要）

| 测试类 | 测试数 | 通过 | 失败 | 错误 | 耗时 |
|---|---|---|---|---|---|
| | | | | | |

### 前端测试详情（verification scripts）

| 测试文件 | 测试数 | 通过 | 失败 |
|---|---|---|---|
| `task-plaza-filter.test.mjs` | | | |
| `task-applications.test.mjs` | | | |
| `task-completion.test.mjs` | | | |
| `task-publish.test.mjs` | | | |
| `my-tasks.test.mjs` | | | |
| `admin-workflows.test.mjs` | | | |

---

## 本地验证记录

> 在 CI 配置完成后，先在本地验证各命令可正常执行。

| 时间 | 命令 | 结果 |
|---|---|---|
| | `cd backend && mvn checkstyle:check` | |
| | `cd backend && cp src/main/resources/application-example.yml src/main/resources/application.yml && mvn test` | |
| | `cd frontend && npm install && npm run lint` | |
| | `cd frontend && npm test` | |
| | `cd frontend && npm run build` | |

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
