# CampusBridge

## 项目简介

CampusBridge 是《软件工程与计算 II》课程大作业项目，目标是构建一个面向校园场景的互助服务平台，为用户提供需求发布、信息浏览、协作匹配与互助交流的统一入口。

当前仓库为项目初始化版本，已完成前后端基础工程搭建、项目目录规划、持续集成配置与基础代码规范检查，可作为后续需求分析、系统设计与功能开发的起点。

### 项目目标

- 支持校园用户发布和浏览互助需求
- 支持基础的用户交互与任务协作流程
- 为后续实现身份认证、信用评价、订单流转等功能预留扩展空间
- 建立规范的团队协作流程，包括分支管理、代码检查和 CI 校验

## 团队成员

| 姓名 | 学号 | 邮箱 | 角色 |
|------|--------|------------------|------|
| 陈亮 | 231098080 | 231098080@smail.nju.edu.cn | 需求负责人 |
| 时铭泽 | 231820103 | 231820103@smail.nju.edu.cn | 架构负责人 |
| 殷晓瑞 | 241250014 | yxr321222@163.com | 开发负责人 |
| 张琼文 | 241250056 | 1661554464@qq.com | 测试负责人 |

## 技术栈

- 前端：Vue 3、Vite、Vue Router
- 后端：Java 17、Spring Boot 3、Maven
- 数据库：MySQL
- 代码托管：GitLab
- 持续集成：GitLab CI
- 代码风格检查：ESLint（前端）、Checkstyle（后端）

## 项目结构

```text
campusbridge/
├─ frontend/      # 前端项目
├─ backend/       # 后端项目
├─ docs/          # 各阶段文档
├─ sql/           # 数据库脚本
├─ .gitlab-ci.yml # GitLab CI 配置
└─ README.md
```

## 本地运行

### 前端

```powershell
cd frontend
npm install
npm run dev
```

常用命令：

```powershell
npm run lint
npm run build
```

### 后端

```powershell
cd backend
mvn clean test
mvn spring-boot:run
```

常用命令：

```powershell
mvn checkstyle:check
```

## 协作与提交流程

- 主分支采用 `main / develop / feature/*` 策略
- 日常开发应从 `develop` 分支切出 `feature/*` 分支完成具体任务
- 提交前应先完成本地代码风格检查与编译检查
- AI 辅助生成的内容需经过人工理解、审查和修改后再提交

## 当前状态

项目当前已完成：

- 前端基础工程初始化
- 后端基础工程初始化
- 基础目录结构搭建
- GitLab CI 流水线配置
- 前后端代码风格检查配置

后续将在此基础上继续推进需求分析、系统设计、数据库设计与功能实现。

P0阶段成果见./docs/P0
