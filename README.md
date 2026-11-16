# CampusBridge

CampusBridge 是校园互助应用，提供社区交流、跑腿与代办任务、校园认证、私聊与站内通知，以及评价积分和管理员处理。任务按“发布—申请—选人—履约—确认—评价”流转；现金报酬作为协商信息记录。

技术栈为 Vue 3 / Vite、Java 17 / Spring Boot、MyBatis、MySQL 8，验证码邮件使用 SMTP，附件使用阿里云 OSS。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [需求规格](docs/srs.md) | 业务范围、角色权限、字段规则、状态条件及验收要求 |
| [系统架构](docs/architecture.md) | 技术选择、模块职责、数据归属、核心协作与一致性设计 |
| [待决事项](docs/open-questions.md) | 尚需选择的业务语义与部署方案 |
| [接口使用说明](docs/api-guide.md) | 当前公共响应、关键调用顺序与动态载荷说明 |
| [OpenAPI](docs/openapi.yaml) | 由代码导出的路由和类型；动态 data 的覆盖限制见接口说明 |
| [数据库 Schema](backend/sql/schema.sql) | 完整表结构、约束和索引 |
| [开发与运行](docs/development.md) | 配置、启动、迁移、构建及分层验证命令 |
| [后端评审](docs/reviews/backend-review-2026-11-07.md) | 既有修复、实现差距和验证证据 |
| [历史归档](docs/archive/README.md) | 原课程材料与校验清单 |

SRS 与架构指导后续开发；现有代码与设计的差距按评审记录处理。工程执行约定见 [AGENTS.md](AGENTS.md)。

## 本地启动

准备 JDK 17、Maven 3.9+、Node.js 20+ 和 MySQL 8。按[运行说明](docs/development.md)初始化数据库，配置数据库连接、JWT 密钥及所需外部服务。

以下命令均从仓库根目录执行，分别放在两个终端：

```bash
(cd backend && mvn spring-boot:run)
```

```bash
(cd frontend && npm ci && npm run dev)
```

前端默认 http://localhost:5173，后端默认 http://localhost:8081；开发代理将 /api 转发到后端。

## 验证入口

```bash
(cd backend && mvn test)
(cd backend && mvn checkstyle:check)
(cd frontend && npm test && npm run lint && npm run build)
```

MySQL 并发与持久化测试需要专用测试库，命令及数据清理范围见[运行说明](docs/development.md#62-真实-mysql-测试)。修改接口后重新执行 OpenAPI 导出；测试执行记录与未覆盖范围见评审。
