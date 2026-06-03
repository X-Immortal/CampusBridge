# CampusBridge — 校园互助平台

## 项目简介

CampusBridge 是一个面向校园场景的互助服务平台，为学生提供任务发布、接单协作、信用评价和站内沟通的一站式体验。项目是《软件工程与计算 II》课程大作业，目前已完成 Phase 4 编码开发，P0 核心功能全部可用，P1 功能基本可用。

**在线体验：https://campusbridge.top**

### 核心功能

| 优先级 | 模块 | 功能 |
| --- | --- | --- |
| P0 | 用户认证 | 注册、登录、JWT 鉴权、校园身份认证（学生证/邮箱/人工审核） |
| P0 | 任务发布 | 发布任务、任务广场筛选、详情查看、草稿、编辑、取消、重发 |
| P0 | 接单匹配 | 接单申请、候选人列表、选择帮助者、自动拒绝其余申请 |
| P0 | 状态流转 | 接单→开始→提交凭证→确认完成，支持驳回与争议 |
| P0 | 内容治理 | 违规关键词拦截、站外联系方式屏蔽 |
| P1 | 信用体系 | 积分调整、等级更新、信用流水 |
| P1 | 消息通知 | 任务事件触发系统通知、站内会话、未读统计 |
| P1 | 超时处理 | 待接单/待确认超时提醒与自动流转 |
| P1 | 举报申诉 | 举报创建、争议联动、管理员裁决与信用扣分 |
| P2 | 管理后台 | 认证审核、举报裁决、任务审核 |

## 团队成员

| 姓名 | 学号 | 邮箱 | 角色 |
| --- | --- | --- | --- |
| 陈亮 | 231098080 | 231098080@smail.nju.edu.cn | 前端开发（成员A） |
| 殷晓瑞 | 241250014 | yxr321222@163.com | 后端开发（成员B） |
| 时铭泽 | 241820103 | 241820103@smail.nju.edu.cn | 后端开发（成员C） |
| 张琼文 | 241250056 | 1661554464@qq.com | 后端开发（成员D） |

## 技术栈

| 层面 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Lucide Icons |
| 后端 | Java 17、Spring Boot 3、Spring Security、MyBatis |
| 数据库 | MySQL 8.x |
| 认证 | JWT（jjwt）|
| 存储 | 阿里云 OSS（头像/附件上传）|
| 邮件 | Spring Mail（SMTP，验证码发送）|
| 代码检查 | ESLint（前端）、Checkstyle（后端） |
| CI/CD | GitLab CI（lint → test → build → deploy）|
| 部署 | 自托管服务器（campusbridge.top）|

## 项目结构

```text
campusbridge/
├── frontend/                          # Vue 3 前端项目
│   ├── src/
│   │   ├── components/                # 公共组件（DashboardShell、StatusBadge）
│   │   ├── views/                     # 页面视图（认证、任务、会话、管理后台等）
│   │   ├── services/                  # API 封装
│   │   ├── composables/               # 组合式函数（认证状态、通知中心）
│   │   └── router/                    # 路由配置
│   └── verification/                  # 前端流程验证脚本（6 组 35 个测试）
├── backend/                           # Spring Boot 后端项目
│   └── src/main/java/com/campusbridge/
│       ├── controller/                # 13 个 REST 控制器
│       ├── service/                   # 20+ 个业务服务接口与实现
│       ├── mapper/                    # MyBatis 数据访问层
│       ├── pojo/                      # 实体与 DTO
│       ├── config/                    # Spring 配置（Security、CORS、OSS 等）
│       ├── filter/                    # JWT 认证过滤器
│       ├── common/                    # 统一响应、错误码、异常处理
│       └── utils/                     # 工具类
├── docs/                              # P0-P4 各阶段文档
│   ├── P0/                            # 项目启动
│   ├── P1/                            # 需求分析
│   ├── P2/                            # 概念设计
│   ├── P3/                            # 详细设计
│   └── P4/                            # 编码开发交付物
├── .gitlab-ci.yml                     # GitLab CI 根配置
├── .gitlab/                           # 前后端子流水线配置
└── README.md
```

## 本地运行

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.x

### 1. 数据库

创建 MySQL 数据库并执行初始化脚本：

```sql
CREATE DATABASE campusbridge DEFAULT CHARACTER SET utf8mb4;
```

数据库表结构由 MyBatis 自动建表（或手动执行 `backend/sql/` 下的脚本）。

### 2. 后端

```powershell
cd backend

# 配置 application.yml 中的数据库连接、邮件服务器等
# 或通过环境变量覆盖敏感配置

mvn spring-boot:run
```

后端默认运行在 `http://localhost:8081`。

### 3. 前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，Vite 自动将 `/api` 请求代理到后端。

## 测试与验证

### 后端

```powershell
cd backend
mvn test                # 运行 34 个单元测试与集成测试
mvn checkstyle:check    # 代码风格检查（0 violations）
```

### 前端

```powershell
cd frontend
npm run lint                    # ESLint 代码检查
npm run build                   # 生产构建
npm run test:tasks              # 任务广场筛选（5 tests）
npm run test:task-applications  # 接单申请流程（4 tests）
npm run test:task-completion    # 完成确认流程（4 tests）
npm run test:task-publish       # 任务发布编辑（11 tests）
npm run test:my-tasks           # 我的任务筛选（9 tests）
npm run test:admin              # 管理员工作台（6 tests）
```

### 最近验证结果

| 类别 | 结果 |
| --- | --- |
| 后端测试 | 34 tests, 0 failures, 0 errors |
| 后端 Checkstyle | 0 violations |
| 前端 ESLint | 通过 |
| 前端构建 | 通过 |
| 前端流程验证 | 35 tests, 0 failures |

## CI/CD

每次提交自动触发 GitLab CI 流水线：

```text
root pipeline
├── backend: lint → test → build → deploy
└── frontend: lint → test → build → deploy
```

- `backend-test` 使用 Maven 运行全部单元测试与集成测试
- `frontend-test` 运行 6 组流程验证脚本
- `deploy` 阶段仅在 `main` 分支触发，自动部署到 campusbridge.top

详细记录见 [`docs/P4/CI-CD配置与运行记录.md`](docs/P4/CI-CD配置与运行记录.md)。

## 演示账号

在线环境可使用以下演示账号，或自行注册：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 普通用户（发布者）| `alice_publisher` | `Campus@12345` |
| 普通用户（接单者）| `bob_helper` | `Campus@12345` |
| 管理员 | `admin` | 以数据库初始化或手动设置为准 |

## 文档

各阶段文档位于 `docs/` 目录：

| 阶段 | 目录 | 内容 |
| --- | --- | --- |
| P0 | `docs/P0/` | 项目启动、团队组建 |
| P1 | `docs/P1/` | 需求分析、用户故事、需求规格说明书 |
| P2 | `docs/P2/` | 概念设计、架构设计 |
| P3 | `docs/P3/` | 详细设计、API 文档、数据库设计 |
| P4 | `docs/P4/` | Sprint 看板、测试记录、CI/CD、实验报告、Bug 日志、演示说明 |

## 协作规范

- 主分支策略：`main`
- AI 辅助生成的内容需经过人工审查和测试验证后再提交
- 提交前确保本地代码风格检查和测试通过