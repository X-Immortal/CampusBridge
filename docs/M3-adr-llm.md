# M3 · LLM 部署 ADR

> 课件出处：00 p13。每组必须提交 **LLM 部署 ADR + 模型路由策略 + 成本预算**。
> 四个必答问题，答不上来这一项就是零分。
>
> **本项目现状**：默认 `ai.model.mode=rule`，不依赖任何真实模型 key 也能演示全链路；
> 只有显式配置 `baseUrl / apiKey / name` 后才走云端模型。
> 2026-11-23 已用 **OpenCode Go + DeepSeek Flash** 跑通一次完整链路（见
> `docs/M3-opencode-run.md`）；下面的成本仍是按网关公式算出的**预算值**，不是供应商账单。

---

## 决策

**采用方案**：☐ 云端模型　☐ 校内/私有模型　☑ 端云混合（默认规则基线，可选云端模型）

**一句话理由**：课程演示必须离线可复现；云端模型只作为「同一契约下的更强理解层」，
配置了就用、挂了就带 `degraded=true` 退回规则基线，绝不把规则结果伪装成模型成功。

---

## 四个必答问题

### Q1 哪些数据可以发送到哪里？

| 数据类别 | 能不能出校/出域 | 发到哪里 | 依据 | 谁批准 |
|---|---|---|---|---|
| 公开需求文本（goal/title/description） | 可以，但默认不出 | 仅当配置了模型且用户使用 llm/auto 时才发云端 | 公开信息，仍需最小化 | 组长 + 模型配置人 |
| 个人偏好（persona/contactPreference） | 默认不出 | 规则基线本地处理 | 偏好属于个人信息 | 组长 |
| 位置/宿舍号 | **默认不能** | 只发校区/楼栋级泛化值，不发宿舍号 | `shareDormRoom=false` + 本体约束 | 组长 |
| 聊天内容 | 只发当前目标与必要上下文 | 不发历史全量会话 | 最小必要原则 | 组长 |
| 信用/评价 | 不出 | 不进入模型上下文 | 与理解任务无关 | 组长 |
| 日志与 trace | 不出 | 本地 `ai_event`，导出前脱敏 | 审计需要 | 组长 |

**脱敏策略**：发送前只保留 `goal + persona + 必要的业务上下文`；`requesterId` 只作为
配额键，不进入 prompt；地点做楼栋/校区级泛化；不发送手机号、宿舍号、真实姓名。

### Q2 一次任务消耗多少 Token 和费用？

估算公式（`ModelGateway.estimateCost`）：
`input = 180 + ceil(goal字数 / 1.5)`，`output = 60 + 槽位数 × 14 + 假设数 × 8`，
单价按 `ai.model.price-per-1k-tokens` 默认 **¥0.002 / 1K token**。

| 任务类型 | 平均输入 Token | 平均输出 Token | 单次成本 | 每天预估次数 | 日成本 | 月成本 |
|---|---:|---:|---:|---:|---:|---:|
| 澄清一轮 | 200 | 150 | ¥0.00070 | 200 | ¥0.14 | ¥4.20 |
| 发布需求 | 220 | 200 | ¥0.00084 | 100 | ¥0.084 | ¥2.52 |
| 比较方案 | 250 | 260 | ¥0.00102 | 100 | ¥0.102 | ¥3.06 |
| 完整任务（按 3 次模型调用保守估） | 600 | 600 | ¥0.00240 | 50 | ¥0.12 | ¥3.60 |

**预算上限**：日模型调用配额 `ai.model.daily-quota=200`（按请求者/自然日），
月度预算按 ¥50 封顶；`price-per-1k-tokens` 可在 `application-demo.yml` 调整。

**超预算怎么办**：配额用尽 → `model.quota.exceeded` 事件 → 规则基线继续服务；
不会排队阻塞用户，也不会静默超支。规则基线不消耗模型配额。

### Q3 模型不可用时如何继续服务？

| 故障类型 | 检测方式 | 降级到哪 | 用户体验 | 恢复后怎么补 |
|---|---|---|---|---|
| 超时 | `ModelException` / HTTP timeout；`ai.fault.mode=model_timeout` 可现场强制 | 规则基线（`degraded=true`） | 卡片照出，明确提示“规则整理/模型不可用” | 事件保留 `model.fallback`；用户可重试，不自动重放业务动作 |
| 429 限流 | 非 200 + 错误信息；连续失败触发熔断 | 规则基线；熔断期内直接走规则 | 同上，不 500 | 冷却后自动恢复；`model.circuit.open` 有记录 |
| 输出不是合法 JSON | JSON 解析失败 → `ModelException` | 规则基线 | 同上 | 只影响本次理解；不污染草稿 |
| 供应商整体不可用 | 熔断 + 连续失败 | 规则基线；L2 固定页面仍可看任务 | 任务列表/详情不受影响 | 恢复后新请求自动回模型 |

**关键**：降级后界面必须**明确说这是规则整理**，不能伪装成模型成功（这条是红线）。
本项目 `AssistantResponse.degraded` + `modelMode` + `notes` 三个字段共同承担这件事；
`ai_event` 里同时有 `model.error` 和 `model.fallback`。

### Q4 为什么为这个任务选择这个模型？

| 任务 | 用什么模型 | 为什么不是别的 | 成本/延迟/能力 的取舍 |
|---|---|---|---|
| 意图理解（取快递/代课/搬运/购买） | 默认 `longcat`；PICKUP/MOVING/PURCHASE 路由到 `longcat-fast` | 短句理解不需要大模型；规则基线已能覆盖常见句式 | 低延迟、低成本；复杂句失败时降级规则 |
| 卡片编排 | 不用模型 | 卡片由 `CardFactory` 确定性生成，模型只给槽位 | 可校验、可复现，避免生成式 UI 越权 |
| 长文本摘要 | 本版本不做 | 课程任务没有长摘要场景 | 不引入不必要的数据出域 |
| 简单分类 | 规则优先 | `RuleModelClient` 的确定性抽取已覆盖 | 能用规则就不调用模型 |
| 复杂/模糊目标 | `longcat-strong`（SUBSTITUTE 路由） | 需要更强理解；但仍受槽位白名单约束 | 成本更高，只给少数任务 |

> **2026-11-23 实测**：用 OpenCode Go 的 `deepseek-v4.1-flash` 单模型跑通完整链路；
> 这次把 `ai.model.routing.enabled=false`，验证的是“模型通道 + 提示词 + 降级”而不是多模型分流。
> 多模型路由的单测在 `ModelGatewayPlanTest`；真实多模型账单还没测。

---

## 路由策略

| 条件 | 路由到 | 理由 |
|---|---|---|
| `context.model` 显式指定 | 指定的模型 | 评测/演示现场可覆盖 |
| persona 命中 | `ai.model.routing.personas` 对应模型 | 不同角色的话术复杂度不同 |
| 任务类型 PICKUP/MOVING/PURCHASE | `longcat-fast` | 短句、字段少，优先延迟 |
| 任务类型 SUBSTITUTE | `longcat-strong` | 涉及时间/身份/风险，需更强理解 |
| 其他/未命中 | `default-model`（默认 `longcat`） | 稳定兜底 |
| 模型未配置 / 熔断 / 配额用尽 | 规则基线 | 可用性优先 |

**版本管理**：每条 `ai_event` 的 payload 由 `VersionInfo` 统一补
`modelVersion / ontologyVersion / promptVersion / schemaVersion / toolVersion`；
版本常量在 `AiProperties`，本体版本来自 `OntologyRegistry` 的 JSON `version`。
回滚只改配置/env，不改事件表结构；旧事件仍带旧版本，可归因。

---

## Model Gateway 的四件事（课件 00 p13）

- [x] **模型路由与版本**：`ModelRouter` 按 persona/任务类型选模型，`model.route` 记录 routeId/model/reason；五类版本进每条事件。
- [x] **配额、缓存与成本**：`ModelQuota` 按日/请求者限流；`ModelCache` 只缓存理解结果（TTL）；`model.cost` 记录估算 token/费用。
- [x] **超时、重试与熔断**：`OpenAiCompatibleClient` 超时；`ModelGateway` 重试、连续失败熔断、降级规则基线。
- [x] **Prompt 与 Context 版本**：`promptVersion` 进事件；上下文只带必要字段，`traceId/requesterId` 不参与缓存 key。

对应测试：`ModelGatewayPlanTest`（路由/缓存/配额/成本）、`FaultSwitchTest`（三个故障开关）、
`EventRecorderVersionTest`（五类版本字段）。命令见 `docs/M2-tests.md` 与根目录 `REVIEW.md`。

---

## 安全

- [x] 密钥只在服务端本地配置（`application.yml` 已进 `.gitignore`），仓库里没有真实 key
- [x] **没有**用任何人泄露出去的 key
- [x] 客户端包里搜不到 key（HAP 只含 App 代码，模型调用全在服务端）
- [x] 日志里不打印 key 和完整 Prompt（只记 mode/model/route/reason/latency）
