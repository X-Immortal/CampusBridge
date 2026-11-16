# CampusBridge 开发与运行说明

**版本：** 1.1 · **更新日期：** 2026-11-16

本文提供当前仓库的配置、启动、数据库重建和验证操作。业务目标见 [SRS](srs.md)，模块与事务设计见[架构](architecture.md)，既有实现差距见[评审记录](reviews/backend-review-2026-11-07.md)。

## 1. 环境与命令位置

使用 JDK 17、Maven 3.9+、Node.js 20+ 和 MySQL 8。仓库位于 WSL 时使用 WSL 的工具链与 Git。以下命令从仓库根目录执行，后端与前端分别在两个终端运行。

```bash
java -version
mvn -version
node --version
mysql --version
```

后端依赖由 backend/pom.xml 管理，前端按 package-lock.json 安装。前端开发端口默认 5173，后端默认 8081。

## 2. 新数据库初始化

在全新目标库上执行：

```bash
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < backend/sql/schema.sql
```

替换 YOUR_DB_USER 为具有建库建表权限的数据库用户。脚本创建并使用 campusbridge，已有库更新结构时按第 5 节重建。应用启动不会自动创建业务表。

backend/sql/reseed_demo_data.sql 会删除并重建演示数据，且写死 USE campusbridge；它不是初始化生产库的默认步骤。使用前检查目标实例、脚本的 DDL 与数据内容，在可丢弃的演示库单独执行。

## 3. 后端配置

首次复制示例，已有本地配置时保留原文件：

```bash
cp -n backend/src/main/resources/application-example.yml backend/src/main/resources/application.yml
```

application.yml 已被忽略。application-example.yml 不会以默认配置文件名自动加载，需复制或显式指定。示例中的环境变量占位符读取启动进程环境；激活 profile 时，profile 专用配置覆盖基础配置中的相同键。

### 3.1 数据库、JWT 和端口

| 配置或环境变量 | 内容 |
| --- | --- |
| MYSQL_URL | JDBC URL，默认示例指向本地 campusbridge，使用 utf8mb4 对应字符配置和 Asia/Shanghai 时间参数 |
| MYSQL_USERNAME / MYSQL_PASSWORD | 应用数据库账号和密码；示例 root/changeme 必须替换 |
| JWT_SECRET | 签名密钥，无默认值；至少 32 字节随机内容 |
| jwt.expiration | 86400000 毫秒，访问令牌期限 |
| jwt.refresh-expiration | 2592000000 毫秒，刷新令牌期限；当前无刷新接口 |
| SERVER_PORT | 默认 8081；变更后同步前端代理或 API 基地址 |

配置通过本地文件或启动环境提供，真实凭据不提交 Git。MySQL URL 中若包含 &，在 shell 中使用引号包裹。

### 3.2 SMTP 与本地验证码

| 配置 | 内容 |
| --- | --- |
| SMTP_HOST / SMTP_PORT | SMTP 地址和端口；示例 465 并启用 SSL |
| SMTP_USERNAME / SMTP_PASSWORD | SMTP 身份和授权密码 |
| campusbridge.verification.email.from | 可选发件地址，默认取 spring.mail.username |
| VERIFICATION_EMAIL_ENABLED | 示例默认 false；当前代码还会依据非空 mail.host 判定需要投递 |
| campusbridge.verification.email.debug-code-enabled | 默认 false；本地开发可显式启用调试回显 |
| VERIFICATION_EMAIL_CODE_SECRET | 验证码校验密钥，替换示例值 |

当前 false 的邮件开关不能单独关闭发送：mail.host 非空时仍可能尝试 SMTP。无邮件服务的本地开发应在 application.yml 中把 spring.mail.host 和 username 设为空，将 email.enabled 设为 false，并显式开启 debug-code-enabled。校园邮箱流程会检查“是否要求实际投递”，保留示例 SMTP 地址可能导致调试模式下仍提交失败。

注册与改邮箱验证码保存在进程内，重启后须重新发送；校园邮箱挑战保存在认证记录中。调试回显只用于开发环境，真实投递验收关闭该开关。

### 3.3 OSS 与文件限制

| 配置或环境变量 | 内容 |
| --- | --- |
| ALIYUN_OSS_ENDPOINT | OSS endpoint |
| ALIYUN_OSS_BUCKET | bucket 名称 |
| ALIYUN_OSS_REGION | 区域，示例 cn-beijing |
| OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET | 当前 OSS SDK 环境凭据提供器读取的密钥 |
| OSS_SESSION_TOKEN | 使用临时安全凭据时提供 |
| spring.servlet.multipart.max-file-size | 默认 5MB |
| spring.servlet.multipart.max-request-size | 默认 10MB |

OSS 凭据来自进程环境，不是 ALIYUN_OSS_ENDPOINT 等位置配置的一部分。未配置 OSS 可运行普通文本流程，不能据此验收头像或附件上传。私有对象的实际读取权限须在真实桶上验证。

### 3.4 超时扫描

| 配置键 | 默认值 |
| --- | --- |
| task.timeout.scan-fixed-delay-ms | 600000 毫秒 |
| task.timeout.scan-initial-delay-ms | 60000 毫秒 |
| task.timeout.scan-limit | 每类候选 100 条 |
| task.timeout.confirmation-reminder-hours | 24 小时 |
| task.timeout.transition-grace-hours | 24 小时 |

这些默认值由服务代码提供，示例 YAML 没有全部列出。业务时间条件见 SRS 第 6.3 节；调试时缩短参数不代表正式业务规则已经改变。

## 4. 启动与构建

后端终端：

```bash
(cd backend && mvn spring-boot:run)
```

前端终端：

```bash
(cd frontend && npm ci && npm run dev)
```

访问 http://localhost:5173。Vite 将 /api 代理到 http://localhost:8081；frontend/src/services/api.js 也支持 VITE_API_BASE_URL。修改后端端口时同时检查这两处配置。

构建：

```bash
(cd backend && mvn package -DskipTests)
(cd frontend && npm run build)
```

构建产物分别在 backend/target 和 frontend/dist。生产运行 JAR 前仍需提供数据库、JWT、OSS 和邮件配置。Vite 开发代理不随静态构建自动成为生产反向代理。

## 5. 数据库重建

数据库结构统一维护在 backend/sql/schema.sql，演示数据由 backend/sql/reseed_demo_data.sql 初始化。结构变更后，停止后端，在仓库根目录执行：

```bash
mysql -h 127.0.0.1 -u YOUR_DB_USER -p -e 'DROP DATABASE IF EXISTS campusbridge;'
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < backend/sql/schema.sql
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < backend/sql/reseed_demo_data.sql
```

上述命令清空 campusbridge 并按最新结构重新创建，随后写入演示数据。完成后启动后端，验证本次结构变更涉及的业务流程。真实 MySQL 测试使用第 6.2 节的专用测试库。

## 6. 按风险选择验证

### 6.1 常规检查

```bash
(cd backend && mvn test)
(cd backend && mvn checkstyle:check)
(cd frontend && npm test)
(cd frontend && npm run lint)
(cd frontend && npm run build)
```

前端 npm test 执行 verification 下的 Node 测试脚本，不等同于真实浏览器端到端测试。后端部分集成测试使用 H2 或 mock Service，不能替代 MySQL 事务与并发测试。

### 6.2 真实 MySQL 测试

先创建两个专用测试库，分别配置账号：

```bash
export CAMPUSBRIDGE_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/campusbridge_task_test'
export CAMPUSBRIDGE_TEST_MYSQL_USER='YOUR_TEST_USER'
export CAMPUSBRIDGE_TEST_MYSQL_PASSWORD='YOUR_TEST_PASSWORD'
export CAMPUSBRIDGE_MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/campusbridge_content_test'
export CAMPUSBRIDGE_MYSQL_TEST_USER='YOUR_TEST_USER'
export CAMPUSBRIDGE_MYSQL_TEST_PASSWORD='YOUR_TEST_PASSWORD'
(cd backend && mvn -Dtest=TaskConsistencyMySqlTest,ContentPersistenceIntegrationTest,ContentQueryIntegrationTest test)
```

| 测试 | 连接与数据影响 | 重点 |
| --- | --- | --- |
| TaskConsistencyMySqlTest | 使用 CAMPUSBRIDGE_TEST_MYSQL_*；数据库名须以 _test 结尾；删除目标库现有表并按 Schema 重建 | 任务竞争、积分、回滚、超时 |
| ContentPersistenceIntegrationTest | 使用 CAMPUSBRIDGE_MYSQL_TEST_*；数据库名必须为 campusbridge_content_test；重建业务表 | 内容、附件、会话及通知 |
| ContentQueryIntegrationTest | 使用 CAMPUSBRIDGE_TEST_MYSQL_*；MySQL 下建连接级临时表，数据库名须以 _test 结尾；未设置时使用 H2 | 聚合筛选、排序、总数与深分页 |

未设置环境变量时，前两组跳过，查询测试退回 H2。命令通过不能自动说明已执行 MySQL 验证；检查报告中具体类及 skipped 数量。

### 6.3 OpenAPI 导出

```bash
(cd backend && mvn -Dtest=OpenApiExportTest -DexportOpenApi=true test)
```

导出文件为 docs/openapi.yaml；测试比较路径和方法集合。部分动态载荷尚不能被完整推导，边界见接口使用说明。生产依赖中不包含测试范围 Springdoc。

## 7. 流程验收与问题定位

演示使用两个普通账号和一个管理员账号。按 SRS 各节验收表先执行任务主流程，再执行越权、并发选人、重复确认、草稿、争议、附件绑定及消息已读场景。验证码调试回显只替代开发邮件接收，不证明 SMTP 可用；真实附件、浏览器交互和目标部署分别验收。

| 现象 | 检查位置 |
| --- | --- |
| JWT 初始化或签名失败 | JWT_SECRET 是否提供、是否满足密钥长度 |
| 验证码失败或请求等待 SMTP | mail.host、发件地址、SSL/端口及邮件开关；本地调试是否清空示例 host |
| 任务发布失败 | 认证状态、字段、未来截止时间、报酬上限、内容检查 |
| 重发失败 | 原任务保存的数据是否完整，截止是否过期；重发请求不接受更新字段 |
| 文件上传失败 | OSS 凭据、endpoint/bucket/region、文件大小及用途头 |
| MySQL 测试被跳过或拒绝 | 环境变量前缀、精确库名和测试账号权限 |
| HTTP 为 2xx 但页面报业务错误 | 检查响应 code；旧接口仍可能用 Result.error 返回 |
| API 请求进入静态页面 | 检查生产入口对 /api 与 SPA 回退的路由顺序 |

单次执行的代码版本、环境、命令及结果记录在评审或交付记录中，不写入本操作说明作为永久通过结论。
