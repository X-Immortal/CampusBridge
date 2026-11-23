# M3 · 压测、SLO 与故障注入

> 课件原话：「M3 不是提交一张架构图，而是用压测、SLO 和故障注入证明系统能够运行。模型超时、工具重复调用和流式连接中断都要实际演练。」（00 p14）
>
> **本轮实测口径**：本机 Windows + Java 23 运行（Maven `--release 17`）+ demo H2 +
> 规则基线（`ai.model.mode=rule`，未配置真实模型 key）。数字是**本机 demo** 的，
> 不代表公网模型延迟；模型相关的成本是网关估算值，见 `docs/M3-adr-llm.md`。

---

## 一、SLO

| 指标 | 目标 | 实测 | 测量方式 | 不达标时怎么办 |
|---|---|---|---|---|
| 会话可用性 | ≥ 99% | **100%**（50/50） | `evals/load/run-load.mjs` 全链路 | 降级规则基线；L2 固定页可看任务 |
| 首字延迟（SSE 首帧）P50 / P95 | ≤ 150ms / ≤ 300ms | **58.4ms / 64.1ms** | 20 并发 `/api/stream`，首帧到达时间 | 前端显示“正在整理”，不空白等待 |
| 卡片生成延迟 P95 | ≤ 800ms | **500.5ms**（完整链路，含追问/选择/确认） | 50 并发 `POST /api/assistant` 全流程 | 超时重试；仍失败走规则卡片 |
| 完整任务成功率 | ≥ 99% | **100%**（50/50） | 全链路走到 `task.created` | 保留会话，允许用户重试/改字段 |
| 工具调用成功率 | ≥ 99.5% | **100%**（10/10 幂等确认，只建 1 单） | 同一 token+key 并发确认 | 返回 DENIED，不落库；记录 `tool.failure` |
| 单次成本 P95 | ≤ ¥0.01 | **规则基线 ¥0**；模型估算 ≤ ¥0.0024 | `model.cost` 估算公式（ADR） | 配额用尽走规则；`model.quota.exceeded` |

> 说明：首帧/完整链路数字来自 `evals/reports/load-2026-11-23T06-27-16-187Z.json`；
> 规则基线不调用模型，所以“单次成本”是 0，模型列是预算值而非账单。

---

## 二、压测

### 2.1 流式会话

| 项 | 值 | 说明 |
|---|---|---|
| 并发长连接数 | 20 | `GET /api/stream?sessionId=...&after=0` |
| 单连接持续时间 | P95 74.8ms | demo 为事件回放，发完 `stream.end` 即收尾 |
| 心跳间隔 | 15s | `StreamController.IDLE_HEARTBEAT_MS` |
| 断线率 | 0%（20/20） | 本轮无主动断线；F3 单独演练中断重连 |
| 内存/连接占用 | 未采集 | 下一轮用 JVM/OS 监控补；本轮只记延迟/错误率 |

### 2.2 模型算力

| 项 | 值 | 说明 |
|---|---|---|
| 并发请求数 | 50 | `POST /api/assistant` 全链路 |
| 输出 Token/s | N/A | 本轮规则基线，未调用真实模型 |
| 上下文长度 | 目标文本 + persona | 不发全量历史；`requesterId` 不进入 prompt |
| 排队长度与等待时间 | 无队列；JVM/Tomcat 线程池 | 50 并发墙钟 514.8ms |
| 背压策略 | 配额 + 熔断 + 规则降级 | 配额用尽不排队，直接降级；不阻塞用户 |

### 2.3 成本容量

| 项 | 值 |
|---|---|
| 单次成本 | 规则基线 ¥0；模型估算 ≤ ¥0.0024/完整任务 |
| 用户配额 | `ai.model.daily-quota=200` 次/请求者/日 |
| 峰值预算 | 月预算 ¥50 封顶（ADR） |
| 缓存命中率与收益 | 单测验证缓存命中只调 1 次模型；压测未单独统计命中率（规则基线不走缓存） |

### 2.4 压测方法（写清楚别人能复现）

- 工具/脚本：`evals/load/run-load.mjs`（Node 22 原生 fetch，无第三方依赖）
- 参数：`--base http://127.0.0.1:8081`；默认 50 并发 assistant、20 并发 stream、10 并发 idempotency；`--concurrency N` 可覆盖
- 环境：Windows + Java 23（release 17）+ demo H2 + 单进程 8081；规则基线，无真实模型 key
- 原始数据存放：`evals/reports/load-2026-11-23T06-27-16-187Z.json`
- 复现命令：

```powershell
# 1) 起 demo 后端（H2，需 test classpath 才有 h2 驱动）
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"

# 2) 跑压测
node evals/load/run-load.mjs --base http://127.0.0.1:8081
```

---

## 三、三个必演的故障（课件点名）

### F1 模型超时

| 项 | 内容 |
|---|---|
| 怎么注入 | 默认 `context.model=unavailable`（无 key 的 llm 降级）；或启动时 `ai.fault.mode=model_timeout` |
| 期望行为 | 降级到规则基线，**界面明确标注“规则整理”/降级**，任务不丢 |
| 实际行为 | `degraded=true`、`modelMode=rule`、返回澄清卡、`taskId=null`；事件 `model.fallback` |
| 恢复 | 下一次请求重新尝试；不自动重放业务动作 |
| 证据 | `node evals/faults/run-faults.mjs --base http://127.0.0.1:8081` → PASS ① |

### F2 工具重复调用

| 项 | 内容 |
|---|---|
| 怎么注入 | 同一 token + 同一 `idempotencyKey` 确认两次（并发 10 次也测过） |
| 期望行为 | 同一幂等键只产生一次业务事实；第二次返回第一次的结果 |
| 实际行为 | 两次均 `outcome=OK`，同一 `taskId`；`task.created` 事件只 1 条；公开任务只 +1 |
| 证据 | `evals/faults/run-faults.mjs` → PASS ②；`evals/load/run-load.mjs` → 唯一 taskId=1、重复建单=0 |

### F3 流式连接中断

| 项 | 内容 |
|---|---|
| 怎么注入 | 客户端读到第 3 帧后 abort，再按 `?after=<lastSeq>` 重连 |
| 期望行为 | 客户端记游标；重连补放；重复事件不重复显示；任务状态不丢 |
| 实际行为 | 首段 3 帧，重连 `after=3` 收 11 帧，序号 4–14 连续，最后一帧 `stream.end` |
| 证据 | `evals/faults/run-faults.mjs` → PASS ③ |

---

## 四、端到端可观测

一个 `trace_id` 要能串起：客户端请求 → Agent → 模型调用 → 工具调用 → 业务写入。

| 环节 | 记了什么 | 在哪看 |
|---|---|---|
| 客户端 | 探针/请求日志（`CampusBridge HTTP <code> ...`） | hdc hilog / DevEco Log |
| Agent | `session.started`、`understanding.applied`、`clarify.asked`、`confirm.requested` | `GET /api/events?sessionId=` |
| 模型网关 | `model.route`、`model.cache.hit/miss`、`model.quota.exceeded`、`model.cost`、`model.fallback`、`model.error` | `GET /api/events`；`ai_event` 表 |
| 工具网关 | `tool.request`、`tool.denied`、`tool.failure`、`tool.success`、`tool.idempotent.replay` | 同上 |
| 业务 | `task.created`（含 taskId/replay） | 同上；`tasks` 表 + `task_histories` |

**演示动作**：现场给一个 trace_id，打开 `/api/events?sessionId=...`，从 `session.started`
翻到 `task.created`；每条事件都带 `modelVersion/ontologyVersion/promptVersion/schemaVersion/toolVersion`。

---

## 五、自查

- [x] SLO 有明确目标值，不是“感觉还行”
- [x] 压测脚本别人能跑（`node evals/load/run-load.mjs --base ...`）
- [x] 三个故障都能现场注入、现场恢复（`node evals/faults/run-faults.mjs` 3/3）
- [x] 原始日志/数据进了 `evals/reports/` 与 `evidence/M3/`
- [x] 降级时界面明确说明了当前是哪一级（Shell 顶部 L2 横条 + 助手按钮禁用）
