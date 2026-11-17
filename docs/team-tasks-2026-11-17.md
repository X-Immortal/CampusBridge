# CampusBridge 2.0 · 任务分配（2026-11-17）

> **适用**：11/17 – 12/13（12/14–12/16 冻结走场）。
> **前提**：主体（环境、App 页面、集成、演示）由殷晓瑞做；组员只写**可独立测试的代码**。
> **依据**：`大作业-4周计划与分工.md`、`大作业任务书.md`、`docs/2.0-design.md`。
> **状态基线**：契约 23/23、后端 283 项测试、评测 40/40、模拟器四卡 + 真实链路已通。**下面列的是还没做的，不要重做已完成部分。**

---

## 0. 一页速览

| 人 | 主线 | 本周（11/23 前）必须交 | 最终截止 | 验收命令 |
|---|---|---|---|---|
| **殷晓瑞** | App 主体 + 集成 + 演示 | App Shell、任务列表/详情、L2 降级、平板断点 | 12/13 | `scripts/build-app.ps1` + 模拟器截图 |
| **谢易轩** | 后端 M3/M4 | 模型路由/配额/缓存/成本 + 三个故障开关 | 11/23（事件版本 11/30） | `mvn -o -q "-Dtest=ModelGatewayPlanTest" test` |
| **张琼文** | 压测/故障/测试 | 三类压测脚本 + 三个故障脚本 + 9 条测试映射 | 11/23（SLO 数字 11/30） | `node evals/load/run-load.mjs`、`node evals/faults/run-faults.mjs` |
| **陆俊达** | M4 记忆/红队/治理 | 记忆三类组件 + 红队五类用例骨架 | 11/30（治理 12/6） | `mvn -o -q "-Dtest=MemoryStoreTest" test`、`node evals/redteam/run.mjs` |

**本周只追三件事**：谢的网关开关、张的压测/故障脚本、陆的记忆组件；页面由殷并行做。11/23 晚拼一次，能拼起来就进 M3 收尾。

---

## 1. 分配原则（先看，避免返工）

1. **只写代码 + 测试**：Java / 纯 TS / Node 脚本 / SQL / JSON。
2. **不碰这三个地方**：
   - `app/**/*.ets`、`app/entry/src/main/ets/pages/**`、`renderer/**`（ArkUI 只有殷写）
   - `contract/src/**`、`app/.../protocol/*`、`transport/events.ts`、`transport/sse.ts`（契约只有殷改）
   - 1.0 的接口路径和字段（`/api/v1/**` 一个都不改）
3. **一人一层，不交叉**：谢管后端网关/事件；张管压测/故障/测试；陆管记忆/红队/治理；殷管 UI/集成/演示。
4. **每项任务一个验收命令**，跑不绿不算交付；PR 里贴命令和输出。
5. **新文件优先**：需要改共享文件（`SecurityConfig`、`pom.xml`、`schema.sql`）先在群里说一声，由殷合并。

---

## 2. 角色卡

### 殷晓瑞（组长 · 交付面）

- **管**：App Shell、任务列表/详情、L2 降级、平板断点、客户端白名单校验、M3 ADR/SLO 终稿、集成、故障演示、PPT/5 分钟脚本、证据包。
- **不写**：后端网关、记忆、红队、压测脚本（这些有人）。
- **本周产出**：
  1. 底部导航 Shell（助手 / 任务）
  2. 任务列表接 `GET /api/v1/tasks`（公开接口）
  3. 任务详情接 `GET /api/v1/tasks/{id}`
  4. L2 降级横幅 + 禁用助手输入
  5. 平板 master-detail（≥840vp 左列表右详情）
  6. 助手页收到卡先过白名单，坏卡拒绝并保留上一张

### 谢易轩（后端）

- **管**：`ModelGateway` 的路由/配额/缓存/成本、`ai.fault.mode` 故障开关、`EventRecorder` 版本字段、trace 贯通核对。
- **不碰**：UI、契约类型、1.0 接口字段、数据库表结构（除非在群里确认）。
- **本周产出**：网关四项能力 + 三个故障开关 + 测试。

### 张琼文（压测/故障/测试）

- **管**：`evals/load/`、`evals/faults/`、`docs/M2-tests.md`、`docs/M3-slo-loadtest.md` 的数字部分。
- **不碰**：产品代码、契约、UI。
- **本周产出**：三类压测脚本、三个故障脚本、9 条测试映射。

### 陆俊达（M4 记忆/红队/治理）

- **管**：`backend/.../memory/`、`evals/redteam/`、`deploy/mysql/governance.sql`、`docs/M4-data.md`、`docs/M4-redteam.md`。
- **不碰**：UI、契约、1.0 接口字段、`AgentOrchestrator`（集成由殷做）。
- **本周产出**：记忆三类组件 + 测试；红队五类用例骨架。

---

## 3. 详细任务

### 3.1 谢易轩

#### 谢-1 · M3 模型路由 / 配额 / 缓存 / 成本（11/23）

| 项 | 内容 |
|---|---|
| 文件 | `backend/.../gateway/model/ModelGateway.java`、新增 `ModelRouter.java`（可选）、`config/AiProperties.java`、新增 `src/test/java/.../ai/ModelGatewayPlanTest.java` |
| 接口 | **保持** `ModelGateway.understand(goal, context, traceId, sessionId)` 签名不变；`ModelResult` 不变 |
| 配置 | `ai.model.routing.*`（按 persona/任务类型选模型）、`ai.model.daily-quota`、`ai.model.cache-ttl-seconds` |
| 事件 | 新增 `model.route`、`model.cache.hit`、`model.cache.miss`、`model.quota.exceeded`、`model.cost`（payload 带估算 token/费用） |
| 边界 | rule 模式行为不变；未配置密钥仍走规则；缓存只缓存理解结果，不缓存业务动作 |
| 验收 | `cd backend && mvn -o -q "-Dtest=ModelGatewayPlanTest" test`；再跑一次全量 `mvn -o test` 不回归 |
| 交付 | 代码 + 测试 + 在 `docs/M3-adr-llm.md` 补“路由/配额/缓存”一节（3–5 行即可） |

#### 谢-2 · M3 三个故障开关（11/23）

| 项 | 内容 |
|---|---|
| 配置 | `ai.fault.mode = none / model_timeout / tool_fail`（默认 none，只在 demo profile 用） |
| 行为 | `model_timeout`：模型调用必超时 → 规则降级；`tool_fail`：`ToolGateway` 返回 DENIED 且不落库；`none`：正常 |
| 文件 | `config/AiProperties.java`、`ModelGateway.java`、`ToolGateway.java`、新增 `FaultSwitchTest.java` |
| 验收 | 三个测试各一条：`degraded=true` / `outcome=DENIED` / 正常建单 |
| 依赖 | 张的故障脚本会读这两条命令；11/21 前先给“能用”的版本，11/23 收尾 |

#### 谢-3 · M4 事件版本字段（11/30）

| 项 | 内容 |
|---|---|
| 文件 | `events/EventRecorder.java`、`config/AiProperties.java`、`OntologyRegistry` 读取版本 |
| 要求 | 每条事件 payload 增加 `modelVersion` / `ontologyVersion` / `promptVersion` / `schemaVersion` / `toolVersion`（没有的写 `unknown`） |
| 验收 | `EventRecorderVersionTest`：一条 `task.created` 事件里五个版本字段都在 |
| 注意 | `ai_event` 表结构不用改，版本放 payload JSON 里 |

#### 谢-4 · trace 贯通核对（11/30）

- 发一次完整请求，导出 `ai_event` 里同 `trace_id` 的 `model.*`、`tool.*`、`task.created` 三类事件，贴到 `evidence/M3/`。
- 不改表结构；如果发现断点，先记录再和殷确认。

---

### 3.2 张琼文

#### 张-1 · 三类压测脚本（11/23）

| 项 | 内容 |
|---|---|
| 文件 | 新增 `evals/load/run-load.mjs` |
| 场景 | ① 50 并发 `POST /api/assistant`（完整链路）② 20 并发 `GET /api/stream`（SSE 长连接）③ 同一 `idempotencyKey` 重复确认 |
| 输出 | `evals/reports/load-<时间戳>.json`：P50/P95/P99、错误率、吞吐、每类场景的配置 |
| 参数 | `--base http://127.0.0.1:8081`、`--scenario all`、`--concurrency N` |
| 验收 | 后端 demo 起着，一条命令跑完，报告能打开、有数字 |
| 注意 | 不要打生产库；用 demo profile（H2） |

#### 张-2 · 三个故障注入脚本（11/23）

| 项 | 内容 |
|---|---|
| 文件 | 新增 `evals/faults/run-faults.mjs` |
| 故障 | ① 模型超时（`ai.fault.mode=model_timeout` 或 `context.model=unavailable`）② 工具重复调用（同幂等键两次）③ 流式中断（收到 N 条后 abort，再按 `after` 重连） |
| 断言 | ① `degraded=true` 且不 500 ② 只产生一个 taskId ③ 重连后事件不重复、序号连续 |
| 输出 | 控制台逐条 PASS/FAIL；退出码 0 才算过 |
| 依赖 | 谢-2 的开关；谢没交付前可先用 `context.model=unavailable` 跑通 ①，②③ 不依赖谢 |

#### 张-3 · 9 条最小测试映射（11/23）

| 项 | 内容 |
|---|---|
| 文件 | 新增 `docs/M2-tests.md` |
| 内容 | 9 行表：课件要求 → 测试文件/用例 → 命令 → 状态（✅/◐/❌） |
| 缺的补 | ⑥ 刷新后恢复 `lastStableVersion`、⑦ 投影移除禁止动作，当前是 ◐；能补就补，补不了写清原因 |
| 验收 | 每一行都能点到一个真实测试文件 |

#### 张-4 · SLO 数字（11/30）

- 把压测结果填进 `docs/M3-slo-loadtest.md`：P95、错误率、并发上限、SSE 时长。
- 不写“大约”“应该”，只写报告里的数字和测试条件。

---

### 3.3 陆俊达

#### 陆-1 · 个人记忆三类（11/30）

| 项 | 内容 |
|---|---|
| 包 | 新增 `backend/src/main/java/com/campusbridge/memory/` |
| 类型 | `DECLARED`（声明）、`INFERRED`（推测，必须带 `inferred=true`）、`TEMPORARY`（临时，带 `expiresAt`） |
| 组件 | `MemoryType`、`PersonalMemory`、`MemoryStore`（TTL 清理）、`MemoryService`、`MemoryController` |
| 接口 | `GET /api/memory?userId=`、`POST /api/memory`、`DELETE /api/memory/{id}`；返回列表/对象即可，不套 1.0 的 `{code,data}` |
| 验收 | `MemoryStoreTest`：临时记忆到期删除、推测标记不可丢、声明可撤回 |
| 注意 | Controller 需要 `SecurityConfig` 白名单，先只写 Service+测试；要接 HTTP 时在群里说，由殷加一行 |

#### 陆-2 · 红队五类（11/30）

| 项 | 内容 |
|---|---|
| 文件 | 新增 `evals/redteam/cases/*.json`、`evals/redteam/run.mjs`；填 `docs/M4-redteam.md` |
| 五类 | 提示注入与越权、恶意生成式 UI、业务规则绕过、模型/网络故障、隐私泄露 |
| 每条 | `attack → result → risk → fix → retest`，后两项不能空 |
| 验收 | `node evals/redteam/run.mjs --base http://127.0.0.1:8081` 输出五类 PASS/FAIL；失败的先记录，修复跟殷/谢排期 |

#### 陆-3 · 数据治理（12/6）

| 项 | 内容 |
|---|---|
| 文件 | 新增 `deploy/mysql/governance.sql`；填 `docs/M4-data.md` |
| 内容 | 数据目录（表/字段/负责人）、质量规则（非空/枚举/外键）、权限矩阵（角色 × 表）、血缘（1.0 表 → 2.0 事件/卡片） |
| 验收 | `governance.sql` 能在 demo 库执行；`M4-data.md` 每一行能指到真实表 |

#### 陆-4 · 飞轮实验（可选，12/6）

- 设计：基线（纯文本对话）vs 干预（四类卡片），5 名组内/同学参与。
- 指标：完成时间、修改次数、误操作、满意度。
- 产出：`docs/M4-evals.md` 加一节 + 两张表；不做也能交 M4，但会少一块分。

---

### 3.4 殷晓瑞（你）

| 任务 | 文件 | 验收 |
|---|---|---|
| App Shell（助手/任务/详情） | `app/.../pages/Shell.ets`、`TaskList.ets`、`TaskDetail.ets` | 模拟器四页切换 + 截图 |
| 任务列表/详情接 1.0 公开接口 | `TaskList.ets`、`TaskDetail.ets` | Agent 建的单能在列表看到 |
| L2 降级 | Shell 状态 + 横幅 | 杀后端/断流后仍能看任务列表 |
| 平板 master-detail | 同一套页面 + 断点 | 平板截图（列表左、详情右） |
| 助手页客户端白名单校验 | `Assistant.ets` | 坏卡被拒，保留上一张 |
| M3 ADR + SLO 终稿 | `docs/M3-adr-llm.md`、`docs/M3-slo-loadtest.md` | 四问全答 + 张的数字 |
| 集成/故障演示/证据/PPT | `evidence/M3`、`evidence/M4`、答辩材料 | G3/G4 清单 |

---

## 4. 排期与闸门

| 时间 | 谁 | 交付 |
|---|---|---|
| **11/21（五）** | 谢 | 故障开关“能用版”先给张 |
| **11/23（日）** | 全员 | 谢-1/2、张-1/2/3、殷 Shell/任务页/L2；晚 20:00 合并验收 |
| **11/30（日）** | 全员 | 谢-3/4、张-4、陆-1/2；G3 预演：三级降级 + 40 条分数 + 三种故障 |
| **12/6（日）** | 全员 | 陆-3（+ 陆-4 可选）、殷 M3 终稿/证据；G3 正式 |
| **12/13（日）** | 全员 | 现场演示不 NG、证据包 M1–M4 齐；G4 |
| **12/14–12/16** | 全员 | 冻结、走场、答辩 |

**G2（11/29）判据**：真机确认后建单、旧卡 409、同一请求只建一单。  
**G3（12/6）判据**：三级降级能现场切、40 条评测出分数、3 种故障有日志。  
**G4（12/13）判据**：5 分钟演示不 NG、M1–M4 证据齐。

---

## 5. 协作规则

### 5.1 分支与合并

- 分支：`feat/m3-model-gateway`（谢）、`test/m3-load-fault`（张）、`feat/m4-memory-redteam`（陆）。
- 合并窗口：**周三、周日**；合并前自己先跑验收命令，PR 里贴输出。
- 冲突处理：谁的分支谁 rebase；共享文件（`SecurityConfig`、`pom.xml`、`schema.sql`）由殷合并。

### 5.2 契约与接口

- `contract/src/**`、`app/.../protocol/*`、`events.ts`、`sse.ts` 只有殷能改；要加字段先发群里，30 分钟内批/不批。
- 1.0 接口路径和字段不改；2.0 新端点写在 `docs/M2-protocol.md`。

### 5.3 每天 18:00 一条

格式：

```
【11/xx 18:00】谁
做了：<命令/功能>
结果：<绿/红 + 关键数字>
卡点：<没有就写无>
```

### 5.4 验收命令合集

```powershell
# 契约
cd CampusBridge\contract; npm run check

# 后端
cd CampusBridge\backend; mvn -o test
mvn -o -q "-Dtest=ModelGatewayPlanTest" test
mvn -o -q "-Dtest=MemoryStoreTest" test

# 评测
cd CampusBridge\evals; node run.mjs --dry-run
node run.mjs --live http://127.0.0.1:8081

# 压测 / 故障 / 红队（张、陆交付后）
node evals/load/run-load.mjs --base http://127.0.0.1:8081
node evals/faults/run-faults.mjs --base http://127.0.0.1:8081
node evals/redteam/run.mjs --base http://127.0.0.1:8081

# App 编译
cd CampusBridge; powershell -ExecutionPolicy Bypass -File .\scripts\build-app.ps1
```

---

## 6. 依赖与风险

| 依赖 | 影响 | 处理 |
|---|---|---|
| 张的故障脚本等谢的开关 | 11/21 前无法跑 ① | 谢 11/21 先交“能用版”；张先用 `context.model=unavailable` |
| 陆的记忆 Controller 等 SecurityConfig 白名单 | HTTP 端点调不通 | 先交 Service+测试，殷加一行白名单 |
| 张的压测会打 demo 后端 | 与殷的 UI 联调抢端口 | 压测固定用 8082 起第二个 demo 实例，或错开时段 |
| 平板断点与页面重构冲突 | 页面文件只有殷改，不会冲突 | 组员不要碰 `pages/**` |
| M4 飞轮需要人 | 没有真实访谈后，只能组内 5 人 | 可选；不影像 M4 其他项 |

---

## 7. 明确不做（防止跑偏）

- 不把 1.0 的 9 个 Vue 页面全搬到 ArkUI（只做助手 + 任务列表/详情 + L2）。
- 不做真 RAG / 向量库。
- 不做仓颉。
- 不做云端部署 / OSS 上传。
- 不做平板/眼镜独立端（同一工程断点）。
- 不追 40 条评测 100% 通过（要分数 + 解释未修项）。

---

## 8. 可直接发群里的版本

> 【任务分配 11/17】
> 规则：只写代码和测试；不碰 `app/**/*.ets`、`contract/src/**`、1.0 接口字段；分支 `feat/...`，合并前验收命令必须绿。
> 谢：模型路由/配额/缓存/成本 + 三个故障开关；11/23 前 `ModelGatewayPlanTest` 绿。
> 张：三类压测 + 三个故障脚本 + 9 条测试映射；11/23 前跑出报告。
> 陆：记忆三类 + 红队五类骨架；11/30 前测试/报告；数据治理 12/6。
> 殷：App Shell/任务页/L2/平板 + ADR/SLO + 集成演示。
> 每天 18:00 报一条：命令、结果、卡点。

---

## 9. 当前基线（已完成，别重做）

- 契约 23/23；后端 283 项测试；评测 40/40。
- `/api/stream` 五种事件；四类卡片；模拟器实拍证据 `evidence/M2/screenshots/`。
- 1.0 业务未改；2.0 只加壳；MyBatis-Plus 主线已适配。
- 已有 `transport/sseParser.ts`、`domain/draftModel.ts`、`scripts/smoke-assistant.mjs`。
