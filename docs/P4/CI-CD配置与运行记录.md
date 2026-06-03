# CI/CD 配置与运行记录

## 配置目标

P4 阶段 CI/CD 目标是让每次提交自动完成以下检查：

- 安装依赖
- 后端 Checkstyle
- 后端单元测试
- 前端 ESLint
- 前端流程验证脚本
- 后端打包
- 前端生产构建
- main 分支部署到服务器目录

## Pipeline 结构

根配置文件：`.gitlab-ci.yml`

```text
root pipeline
├─ backend child pipeline: .gitlab/backend.yml
└─ frontend child pipeline: .gitlab/frontend.yml
```

## 后端流水线

配置文件：`.gitlab/backend.yml`

| Job | Stage | 镜像 | 命令 | 产物 |
|---|---|---|---|---|
| `backend-lint` | lint | `maven:3.9.9-eclipse-temurin-17` | `mvn checkstyle:check` | 无 |
| `backend-test` | test | `maven:3.9.9-eclipse-temurin-17` | `mvn test` | `target/surefire-reports/` |
| `backend-build` | build | `maven:3.9.9-eclipse-temurin-17` | `mvn clean package -DskipTests` | `backend/target/backend-*.jar` |
| `backend-deploy` | deploy | GitLab Runner | 复制 jar 并重启 `campusbridge` 服务 | 仅 main 分支 |

## 前端流水线

配置文件：`.gitlab/frontend.yml`

| Job | Stage | 镜像 | 命令 | 产物 |
|---|---|---|---|---|
| `frontend-lint` | lint | `node:20` | `npm install && npm run lint` | 无 |
| `frontend-test` | test | `node:20` | 运行 6 组 `verification/*.test.mjs` 脚本 | 无 |
| `frontend-build` | build | `node:20` | `npm install && npm run build` | `frontend/dist/` |
| `frontend-deploy` | deploy | GitLab Runner | 复制 `dist` 到 `/opt/campusbridge/static/` | 仅 main 分支 |

## 本地等价运行记录

由于课程提交前无法在文档中直接附 GitLab 页面截图，本记录使用与 CI 相同的本地命令作为等价运行结果。

| 时间 | 命令 | 结果 |
|---|---|---|
| 2026-06-03 | `cd backend && mvn checkstyle:check` | BUILD SUCCESS，0 Checkstyle violations |
| 2026-06-03 | `cd backend && mvn test` | BUILD SUCCESS，34 tests, 0 failures, 0 errors |
| 2026-06-03 | `cd frontend && npm run lint` | 通过，无错误 |
| 2026-06-03 | `cd frontend && npm run build` | 通过，生成 `dist/` |
| 2026-06-03 | `npm run test:tasks` | 5 tests passed |
| 2026-06-03 | `npm run test:task-applications` | 4 tests passed |
| 2026-06-03 | `npm run test:task-completion` | 4 tests passed |
| 2026-06-03 | `npm run test:task-publish` | 11 tests passed |
| 2026-06-03 | `npm run test:my-tasks` | 9 tests passed |
| 2026-06-03 | `npm run test:admin` | 6 tests passed |

## 最近一次验证摘要

```text
Backend:
- Checkstyle: 0 violations
- Test suite: 34 passed / 0 failed

Frontend:
- ESLint: passed
- Build: passed
- Verification scripts: 35 passed / 0 failed
```

## 部署说明

`backend-deploy` 与 `frontend-deploy` 仅在 `main` 分支触发，且依赖带有 `campusbridge-runner` 标签的 Runner：

- 后端 jar 部署到 `/opt/campusbridge/backend.jar`
- 前端静态资源部署到 `/opt/campusbridge/static/`
- 后端服务通过 `systemctl stop/start campusbridge` 重启