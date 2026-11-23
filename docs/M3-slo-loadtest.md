# M3 · 压测、SLO 与故障注入

---

## 一、SLO

| 指标 | 目标 | 实测（本轮真实数字） | 测量方式 | 不达标时怎么办 |
|---|---|---|---|---|
| 会话可用性 | 待定（殷/产品定 SLO 目标） | 并发 300 内完整链路成功 **300/300，错误率 0** | `run-load.mjs --scenario assistant` 的 `success/units`、`errorRate` | 查 `ai_event` 同 trace 断点；确认规则基线是否兜住 |
| 首字延迟（TTFT）P50 / P95 | 待定 | **N/A（本轮未测）**：demo 默认 `ai.model.mode=rule`，无模型流式首字 | —— | 接 llm + 密钥后由谢-1 复测；rule 下记 N/A |
| 卡片生成延迟 P95 | 待定 | **未单独采样**：`run-load` 计的是完整链路（start→confirm）；首卡单跳是其下界 | 见 2.2 阶梯表 | 需拆分「只测 start」场景才有精确首卡数 |
| 完整任务成功率 | 待定 | **100%**（20/50/100/150/300 各档 errorRate=0） | `run-load.mjs` `errorRate` | 见 `docs/failures.md` 追因 |
| 工具调用成功率 | 待定 | confirm 建单全成功；幂等场景 10 并发同键→仅 1 个 taskId | `run-faults.mjs` F2 | 查 ToolGateway DENIED 原因（认证/规则） |
| 单次成本 P95 | 待定 | **N/A**：rule 模式无模型调用=0；token/费用估算待谢-1 的 `model.cost` 事件 | —— | 归谢-1；本轮记 N/A |

---

## 二、压测

### 2.1 流式会话

| 项 | 值（本轮真实数字） | 说明 |
|---|---|---|
| 并发长连接数 | 双档 20 / 60，均 0 断线 | `run-load.mjs --scenario stream` |
| 单连接持续时间 | 并发 20：P50=211ms / P95=215ms；并发 60：P50=35ms / P95=55ms | 会话已建单、事件含 `stream.end`，故能正常收尾 |
| 心跳间隔 | 15s | `StreamController.IDLE_HEARTBEAT_MS=15000`（代码常量） |
| 断线率 | 0/20、0/60（`endedCount` 全命中） | 报告 `stream.endedCount` |
| 内存/连接占用 | **未测** | H2 单实例，需 JMX/`-Xmx` 采样，本轮未采 |
| 连接超时上限 | 120s | `StreamController.CONNECTION_TIMEOUT_MS=120000`（代码常量） |

### 2.2 模型算力

> 本轮 `ai.model.mode=rule`（无模型密钥）：下面是「理解层走规则基线」的吞吐/延迟；llm 模式的 Token/s、上下文、排队待谢-1 接密钥后复测。

| 项 | 值（本轮真实数字） | 说明 |
|---|---|---|
| 并发请求数 | 阶梯 20→50→100→150→300 | `run-load.mjs --scenario assistant --concurrency N` |
| 完整链路延迟（start→confirm） | 见下方阶梯表 | 单机 loopback，rule 模式 |
| 输出 Token/s | **N/A**（rule 不产 token） | 待 llm |
| 上下文长度 | **N/A** | 待 llm |
| 排队长度与等待时间 | **未采** | 需网关暴露队列指标，归谢-1 |
| 背压策略 | **未测** | 队列满拒/等由谢-1 定义 |

**完整链路阶梯（demo/H2 单实例 · loopback · 预热后；原始报告见 `evals/reports/load-*.json`）**

| 并发 | P50(ms) | P95(ms) | P99(ms) | max(ms) | 错误率 | 吞吐(/s) | 成功 |
|---|---|---|---|---|---|---|---|
| 20 | 246 | 334 | 344 | 344 | 0 | 52.1 | 20/20 |
| 50 | 538 | 703 | 720 | 720 | 0 | 65.6 | 50/50 |
| 100 | 1078 | 1359 | 1392 | 1395 | 0 | 69.2 | 100/100 |
| 150 | 1191 | 1518 | 1554 | 1557 | 0 | 92.3 | 150/150 |
| 300 | 2126 | 2664 | 2698 | 2715 | 0 | 107.5 | 300/300 |

> **并发上限结论**：300 并发内**未出现错误/失败**——上限不是「报错拐点」，而是延迟随并发近似线性上升（排队效应）。以 P95≤3s 为参考线，实测可稳定服务约 **300 并发（P95≈2.66s）**；更高档未测。条件：demo/H2 单实例、规则基线、本机 loopback，非生产。

### 2.3 成本容量

| 项 | 值 |
|---|---|
| 单次成本 | |
| 用户配额 | |
| 峰值预算 | |
| 缓存命中率与收益 | |

### 2.4 压测方法（写清楚别人能复现）

- 工具/脚本：`evals/load/run-load.mjs`（Node，内置 `fetch`，无第三方依赖）
- 参数：`--base http://127.0.0.1:8081 --scenario all|assistant|stream|idempotency --concurrency N`
- 环境（机器、网络、模型）：单台 Windows 本机；loopback（127.0.0.1）；后端 `demo` profile（H2 内存库、`ai.model.mode=rule`、Tomcat 8081）；无模型密钥
- 原始数据存放：`evals/reports/load-<时间戳>.json`（每档一份，含 P50/P95/P99、错误率、吞吐、事件数、`taskIdCount`）；打包进 `evidence/M3/` 由殷统一收
- 起服务注意：`pom.xml` 里 `h2` 是 `test` scope，`mvn spring-boot:run` 起 demo 会缺 H2 驱动；本轮用「导出含 test scope 的 classpath + `java` 直接起」绕过，未改 `pom.xml`

---

## 三、三个必演的故障（课件点名）

### F1 模型超时

| 项 | 内容 |
|---|---|
| 怎么注入 | 本轮：`POST /api/assistant` 带 `context.model=unavailable`（免重启）；谢-2 交付后可用 `ai.fault.mode=model_timeout` 复现 |
| 期望行为 | 降级到规则基线，**界面明确标注"规则整理"**，任务不丢 |
| 实际行为 | ✅ 实测通过：`degraded=true`、HTTP **200（不 500）**、仍返回规则基线卡片 |
| 恢复 | 去掉 `context.model=unavailable` 即回正常路径；降级带原因返回，事件有记录 |
| 证据 | `evals/faults/run-faults.mjs` F1 **PASS**（`status=200 degraded=true 有卡片=true`），退出码 0 |

### F2 工具重复调用

| 项 | 内容 |
|---|---|
| 怎么注入 | 同一个确认连点 / 直接重放请求两次 |
| 期望行为 | 同一幂等键只产生一次业务事实；第二次返回第一次的结果 |
| 实际行为 | ✅ 两次确认返回同一 `taskId`，去重后 **1 个**，仅一条业务事实 |
| 证据 | `run-faults.mjs` F2 **PASS**（`两次 taskId=[1,1] 去重后=1`）；对应 `AgentFlowTest.确认后建单_重复确认不再建单` |

### F3 流式连接中断

| 项 | 内容 |
|---|---|
| 怎么注入 | 脚本收到 N 条事件后 `abort`，再按 `after=<末序号>` 重连（等价流到一半断网/杀连接） |
| 期望行为 | 客户端记游标；重连 `?after=<cursor>` 补放；重复事件不重复显示；任务状态不丢 |
| 实际行为 | ✅ 段1 读 3 条后 abort（末序号 3），段2 按 `after=3` 重连从序号 **4** 续；合并序号 **1..14 连续、无重复**、补到 `stream.end` |
| 证据 | `run-faults.mjs` F3 **PASS**（合并序号 `1,2,…,14`；`noDup && contiguous && resumedRight && gotEnd` 全真） |

---

## 四、端到端可观测

一个 `trace_id` 要能串起：客户端请求 → Agent → 模型调用 → 工具调用 → 业务写入。

| 环节 | 记了什么 | 在哪看 |
|---|---|---|
| 客户端 | | |
| Agent | | |
| 模型网关 | | |
| 工具网关 | | |
| 业务 | | |

**演示动作**：现场给一个 trace_id，把这条链路从头翻到尾。

---

## 五、自查

- [ ] SLO 有明确目标值，不是"感觉还行"
- [ ] 压测脚本别人能跑
- [ ] 三个故障都能现场注入、现场恢复
- [ ] 原始日志/数据进了 `evidence/M3/`
- [ ] 降级时界面明确说明了当前是哪一级
