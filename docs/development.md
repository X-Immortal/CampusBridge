# CampusBridge 开发与运行说明

**版本：** 1.1 · **更新日期：** 2026-11-16

本文提供当前仓库的配置、启动、数据库重建和验证操作。业务目标见 [SRS](srs.md)，模块与事务设计见[架构](architecture.md)，既有实现差距见[评审记录](reviews/backend-review-2026-11-07.md)。

## Docker 开发与测试环境

安装 Docker Engine 与 Compose 插件。两套环境共享 deploy/mysql 下的建表、演示数据和字符集配置，并使用各自的命名数据卷。

| 环境 | Compose 目录 | 运行内容 | 宿主机入口 |
| --- | --- | --- | --- |
| dev | deploy/dev | MySQL；前后端在本机运行，支持 IDE 调试与 Vite 热更新 | MySQL 默认 127.0.0.1:3306，可用 MYSQL_PORT 修改 |
| test | deploy/test | MySQL、Spring Boot、前端静态服务与入口 Nginx | HTTP 默认 127.0.0.1:8080，MySQL 默认 127.0.0.1:3307；分别由 HTTP_PORT、MYSQL_PORT 控制 |

### dev：本地开发

从仓库根目录复制配置：

```bash
cp -n deploy/dev/.env.example deploy/dev/.env
```

填写 MYSQL_ROOT_PASSWORD 和 MYSQL_PASSWORD，分别用于数据库管理员与 campusbridge 应用账号。如果本机 3306 已被占用，修改 MYSQL_PORT 后启动：

```bash
(cd deploy/dev && docker compose up -d --wait)
```

按第 3 节配置本地后端：数据库地址使用 127.0.0.1 和 MYSQL_PORT，账号为 campusbridge，密码与 deploy/dev/.env 中 MYSQL_PASSWORD 一致；另行设置 JWT_SECRET。Compose 的 .env 只供编排读取，不会自动导出到本机 Java 进程。前后端按第 4 节分别启动，浏览器访问 http://localhost:5173。

### test：完整容器测试

从仓库根目录复制配置：

```bash
cp -n deploy/test/.env.example deploy/test/.env
```

填写 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD 和 JWT_SECRET；JWT_SECRET 可用 `openssl rand -hex 32` 生成。然后执行：

```bash
cd deploy/test
docker compose up -d --build --wait
docker compose ps
```

默认访问 http://localhost:8080。HTTP 入口与 MySQL 调试端口均只绑定宿主机 127.0.0.1。前后端通信走 Compose 网络，后端始终连接 mysql:3306，宿主机数据库工具连接 MYSQL_PORT。

| 服务 | 职责 |
| --- | --- |
| gateway | Nginx 入口，将 /api/ 转发到后端，其余请求转发到前端；健康检查访问页面与内容查询接口 |
| frontend | 使用 Node.js 24 LTS 构建 Vue 静态文件，由 Nginx 托管并支持前端路由刷新 |
| backend | 使用 Java 17 运行 Spring Boot JAR，等待数据库健康后启动 |
| mysql | MySQL 9.7.4 LTS，使用 utf8mb4、+08:00 数据库时区及命名数据卷 |

Spring Boot 默认加载已提交的 application.yml，环境变量统一覆盖连接和凭据；Docker 构建上下文排除 application-local.*、.env、测试源码和本地构建产物。前端 API 使用同源 /api 地址。

默认不配置 SMTP，通过 VERIFICATION_DEBUG_CODE_ENABLED=true 在验证码接口响应中返回 debugCode。测试真实邮件时填写 SMTP 配置并关闭调试回显。上传真实附件需要配置 OSS；所有配置填写在 deploy/test/.env，字段说明见第 3 节。

### 数据初始化与日常操作

两套环境首次使用空数据卷时均按顺序执行 deploy/mysql/schema.sql 和 reseed_demo_data.sql，导入示例账号、帖子和任务。admin、chenyue 等演示账号的密码均为 password。示例头像 URL 是占位地址。

在对应的 deploy/dev 或 deploy/test 目录执行：

```bash
docker compose ps
docker compose logs -f
docker compose down
```

服务使用 unless-stopped 重启策略，后端停止等待时间为 30 秒。down 保留该环境的数据库卷。test 代码更新后运行 `docker compose up -d --build --wait` 重新构建。结构变更后，在对应环境目录执行以下命令，清除该环境数据并按最新 SQL 重建：

```bash
docker compose down -v
docker compose up -d --build --wait
```

已有数据卷不会重新执行初始化脚本。建表与演示数据的唯一维护位置是 deploy/mysql。

## 1. 本地开发环境与命令位置

使用 JDK 17、Maven 3.9+、Node.js 24 LTS 和 MySQL 9.7 LTS。仓库位于 WSL 时使用 WSL 的工具链与 Git。以下命令从仓库根目录执行，后端与前端分别在两个终端运行。

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
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < deploy/mysql/schema.sql
```

替换 YOUR_DB_USER 为具有建库建表权限的数据库用户。脚本创建并使用 campusbridge，已有库更新结构时按第 5 节重建。应用启动不会自动创建业务表。

需要示例账号、帖子和任务时，执行 deploy/mysql/reseed_demo_data.sql；脚本清空 campusbridge 中的原有业务数据并重新填充演示数据。

## 3. 后端配置

backend/src/main/resources/application.yml 是受 Git 跟踪的公共主配置，Spring Boot 启动时自动加载。数据库、JWT、邮件和 OSS 的环境差异通过环境变量注入。

首次本地开发时复制精简覆盖模板：

```bash
cp -n backend/src/main/resources/application-local.example.yml backend/src/main/resources/application-local.yml
```

填写数据库密码和 JWT 密钥；数据库密码与 deploy/dev/.env 中 MYSQL_PASSWORD 一致。需要覆盖端口或外部服务时，再添加对应配置项。主配置自动导入 application-local.yml，实际私有文件被 Git 忽略；本地覆盖文件与模板均从 JAR 和 Docker 构建中排除。

自动化测试使用 test Profile，不导入本地覆盖；测试配置清空 SMTP 地址，并提供测试数据库与业务参数。本地直连不处理转发头，test Compose 在入口 Nginx 后使用 framework 转发头处理。

### 3.1 数据库、JWT 和端口

| 配置或环境变量 | 内容 |
| --- | --- |
| MYSQL_URL | JDBC URL，默认指向本地 campusbridge，使用 utf8mb4 对应字符配置和 Asia/Shanghai 时间参数 |
| MYSQL_USERNAME / MYSQL_PASSWORD | 应用数据库账号和密码；本地连接账号和密码，需与数据库环境一致 |
| JWT_SECRET | 签名密钥，无默认值；至少 32 字节随机内容 |
| jwt.expiration | 86400000 毫秒，访问令牌期限 |
| jwt.refresh-expiration | 2592000000 毫秒，刷新令牌期限；当前无刷新接口 |
| SERVER_PORT | 默认 8081；变更后同步前端代理或 API 基地址 |

配置通过本地文件或启动环境提供，真实凭据不提交 Git。MySQL URL 中若包含 &，在 shell 中使用引号包裹。

### 3.2 SMTP 与本地验证码

| 配置 | 内容 |
| --- | --- |
| SMTP_HOST / SMTP_PORT | SMTP 地址默认空；端口默认 465 并启用 SSL |
| SMTP_USERNAME / SMTP_PASSWORD | SMTP 身份和授权密码 |
| campusbridge.verification.email.from | 可选发件地址，默认取 spring.mail.username |
| VERIFICATION_EMAIL_ENABLED | 默认 false；当前代码还会依据非空 mail.host 判定需要投递 |
| VERIFICATION_DEBUG_CODE_ENABLED | 普通启动默认 false；Docker test 默认 true，控制验证码调试回显 |
| VERIFICATION_EMAIL_CODE_SECRET | 验证码校验密钥，替换示例值 |

mail.host 非空时，当前邮件实现仍会尝试 SMTP，即使 email.enabled=false。无邮件服务的本地开发保持 SMTP_HOST 和 SMTP_USERNAME 为空，将 VERIFICATION_EMAIL_ENABLED 设为 false，并显式设置 VERIFICATION_DEBUG_CODE_ENABLED=true。

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

这些默认值由服务代码提供，主配置没有全部列出。业务时间条件见 SRS 第 6.3 节；调试时缩短参数不代表正式业务规则已经改变。

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

构建产物分别在 backend/target 和 frontend/dist。直接运行 JAR 时仍需提供数据库、JWT、OSS 和邮件配置。test 环境的 API 转发由入口 Nginx 提供。

## 5. 数据库重建

数据库结构统一维护在 deploy/mysql/schema.sql，演示数据由 deploy/mysql/reseed_demo_data.sql 初始化。结构变更后，停止后端，在仓库根目录执行：

```bash
mysql -h 127.0.0.1 -u YOUR_DB_USER -p -e 'DROP DATABASE IF EXISTS campusbridge;'
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < deploy/mysql/schema.sql
mysql -h 127.0.0.1 -u YOUR_DB_USER -p < deploy/mysql/reseed_demo_data.sql
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

导出文件为 docs/openapi.yaml；测试比较路径和方法集合，并验证任务分页与嵌套泛型分页的响应类型。公共响应和错误语义见接口使用说明。生产依赖中不包含测试范围 Springdoc。

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
| 认证或上传失败 | 检查非 2xx 响应的 code/message；502 对应外部邮件或存储服务失败，500 对应服务端处理或保存失败 |
| API 请求进入静态页面 | 检查 test 入口对 /api 与 SPA 回退的路由顺序 |

单次执行的代码版本、环境、命令及结果记录在评审或交付记录中，不写入本操作说明作为永久通过结论。
