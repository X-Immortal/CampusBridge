# M1 · 追溯矩阵

> 课件原话：「追溯矩阵中的每一行都必须能从『需求说明』跳到『代码位置』再跳到『测试证据』」（02 p36）
> 「五组资产必须相互追溯：某个需求使用哪些概念，进入哪段代码，触发哪些追问和卡片，由哪些测试证明。」（02 p70）

**这是评审老师的第一入口。** 他一坐下就会让你从某一行开始，一路点到测试。

**代码坐标的写法**：用 `文件#方法()` 而不是 `文件:行号` —— 行号每次提交都会漂，
方法名在 IDE 里可以直接搜到。需求编号 `R-xx` 对应 [M1-needs.md](M1-needs.md)，约束编号 `Kxx` 对应
[M1-ontology.md](M1-ontology.md) 与本体 JSON。

---

## 主表（18 条微需求）

状态说明：✅ 已打通（需求 → 代码 → 事件 → 卡片 → 测试 全链）；◐ 部分（只落了偏好/槽位，没有专有交互）；☐ 缺口（已进 `failures.md`，随 M2/M4 补）。

| # | 需求（用户原话摘要） | 本体概念 / 约束 | 代码位置 | 领域事件 | 卡片 | 测试 / 评测 | 状态 |
|---|---|---|---|---|---|---|---|
| R-01 | “能找到人解决问题”最重要 | C02 Goal、C09 Preference | `gateway/model/RuleModelClient#detectIntent`；`agent/AgentOrchestrator#optionsFor` | `understanding.applied` | comparison（“立即匹配”排第一） | `M1-005`、`RuleModelClientTest#课件原句_晚上九点取快递预算八元` | ◐ |
| R-02 | 30 分钟内、只接受静默通知 | C09 Preference（临时）、C17 ClarificationQuestion | `domain/DraftField#CONTACT_PREFERENCE`；`agent/ClarificationPolicy#questionsFor` | `clarify.asked` | clarification | `M1-010` | ◐ |
| R-03 | 新生不认识取件点，需要先解释路线 | C12 Location | `gateway/model/RuleModelClient#matchLocation` | `understanding.applied` | confirmation 的 Summary 行 | `M1-018` | ◐ |
| R-04 | 留学生要用英语、地址要可理解 | C12 Location、C18 Answer | `gateway/model/RuleModelClient`（只抽时间） | `clarify.asked` | clarification | `M1-018` | ☐ |
| R-05 | 社团负责人需要多人/名额 | C03 Task（**缺** Participant 概念） | — | — | — | — | ☐ |
| R-06 | 熟人合作没有隔阂 | C09 Preference、K15 | `domain/TaskDraft#sourceOf`；`policy/ConstraintEvaluators#inferredPreferenceConfirmation` | `understanding.applied` → `confirmation.issued` | confirmation（“系统推测”标记） | `AgentFlowTest#改过内容之后旧确认卡失效` | ◐ |
| R-07 | 倾向无接触交接，物品放指定地点 | C09 Preference、K11 | `domain/DraftField#ONLY_IN_APP_CONTACT`；`policy/ConstraintEvaluators#contactPolicy` | `policy.rejected`（关闭双方联络时） | confirmation | `PolicyEngineTest#K11_两种联络方式都关被拒` | ✅ |
| R-08 | 发烧时让舍友带药，一次性情境 | C18 Answer（临时作用域） | `domain/TaskDraft#Source`、`#remove` | `clarify.answered` | clarification | `TaskDraft` 相关单测 | ◐ |
| R-09 | 必须有严格校内认证才敢发单 | C08 CampusVerification | 1.0 `service/impl/TaskServiceImpl#requireVerifiedUser`（复用） | `tool.failure` | progress（失败卡片） | `M1-015` | ✅ |
| R-10 | 站内聊天必要，不想频繁加微信 | C09 Preference、K11 | `domain/DraftField#ONLY_IN_APP_CONTACT`（默认 true） | — | confirmation 的 Summary 行 | `PolicyEngineTest#K11_两种联络方式都关被拒` | ✅ |
| R-11 | 先看报价，更看能力和成功率 | C15 Reward | `gateway/model/RuleModelClient#applyReward` | — | confirmation 的“报酬”行 | `M1-009` | ◐ |
| R-12 | 要有凭证，求助者能确认/驳回 | C05 CompletionProof | `domain/DraftField#REQUIRES_PROOF`；`tool/ServiceTaskCommandAdapter#createTask` | `tool.success` | confirmation 的“凭证”行 | `M1-006` | ✅ |
| R-13 | 怕被放鸽子、临时爽约 | C11 TimeWindow、C07 CreditEvent | `policy/ConstraintEvaluators#deadlineFuture` | `policy.rejected` | confirmation | `PolicyEngineTest#K04_过去的截止时间被拒` | ◐ |
| R-14 | 流程太长不如群里一问 | C03 Task、B4 澄清策略 | `agent/ClarificationPolicy#questionsFor`（一次最多 3 条 + 默认值） | `clarify.asked` | clarification | `M1-008` | ◐ |
| R-15 | “一瓶饮料就行”“说声谢谢就可以” | C15 Reward（NEGOTIABLE） | `gateway/model/RuleModelClient#applyReward` | — | confirmation 的“报酬说明”行 | `M1-009` | ✅ |
| R-16 | 只信完成过多单、有历史记录的人 | C07 CreditEvent（ruleId 未落） | 缺口见 `failures.md#F-04` | — | — | — | ☐ |
| R-17 | 需要高级筛选，而不是信息流里翻 | C02 Goal、C03 Task | 缺口见 `failures.md#F-05` | — | comparison 选项（仅三种） | — | ☐ |
| R-18 | 经常选择晚间取件（推测） | C09 Preference、C13 RiskLevel | `gateway/model/RuleModelClient#riskOf` | `understanding.applied` | confirmation（风险提示块） | `M1-005` | ◐ |

**统计**：✅ 5 条 · ◐ 9 条 · ☐ 4 条（缺口都写进了 [failures.md](failures.md)，有 owner 和补的时间点）。

> 为什么允许缺口留在表里：课件要的是**可追溯**，不是**全做完**。把没做的藏起来，
> 现场被问一句就露馅；写在表里反而能讲清「我们知道自己差在哪」。

---

## 五组资产覆盖检查（课件 02 p70）

| 资产 | 最少内容 | 对应到哪几行 | 状态 |
|---|---|---|---|
| 1 个性化需求与证据库 | 5 类人物 / 15 条微需求 | 本表 18 行；[M1-needs.md](M1-needs.md) | ☑ 18 条，证据 26 处访谈引文 |
| 2 FDE 现场记录 | ≥3 名真实用户 + 任务旅程 | [M1-interviews.md](M1-interviews.md)（4 份 2026 春访谈为二手证据；本学期 ≥3 场待补） | ◐ |
| 3 本体 V0.1 | 15 概念 / 20 关系 / 10 约束 | [M1-ontology.md](M1-ontology.md)：20 / 23 / 15 | ☑ |
| 4 可运行 Agent Harness | 真模型 + 规则基线可切换 | `gateway/model/ModelGateway`；`POST /api/assistant` | ☑ |
| 5 对话、卡片与评测 | 3 流程 / 4 卡片 / 15 评测 | `evals/cases`：18 条；四类卡片见 `schema/CardFactory` | ☑ |

---

## 六类知识点的最小证据（课件 02 p36）

| 知识点 | 最小实现 | 我们的证据 | 有没有 |
|---|---|---|---|
| 候选不等于事实 | 候选态与确认态分离，确认前禁止写库 | `AgentSession` 状态机 + `ToolGateway` 只在 `confirm` 时调 `TaskService`；`AgentFlowTest` 断言建单次数 | ☑ |
| 证据账本 | source / status / scope / expiresAt + 原始表达 | `TaskDraft.Source`（USER/INFERRED/DEFAULT）→ 卡片 Summary 的「用户说的 / 系统推测」；[M1-needs.md](M1-needs.md) §三 | ☑ |
| 必要追问 | 独立 Policy，按缺失与风险筛问题 | `agent/ClarificationPolicy`（与 Prompt 完全解耦）+ `clarify.asked` 事件 | ☑ |
| 结构化输出治理 | Schema / 白名单 / 枚举 / 失败拒绝 | `schema/SchemaValidator` + `POST /api/schema/validate`；`M1-012/013/014` | ☑ |
| 抛弃式交互 | Schema 驱动确认卡，UI 与业务状态解耦 | `schema/CardFactory` 四类卡片；确认卡带版本与令牌 | ☑ |
| 数据飞轮 | 记录纠正、放弃、失败与结果 | `events/EventRecorder` 已记录 `clarify.answered`、`policy.rejected`、`session.cancelled`、`task.created` | ◐（M4 补全 16 类） |

---

## 现场演示路径（练熟这 90 秒）

1. 打开本表，随手指一行，比如 **R-07**；
2. 说需求原话 → 指出本体概念 `C09 Preference` 与约束 **K11**；
3. 跳到 `policy/ConstraintEvaluators#contactPolicy`，10 秒讲清判定；
4. 跑对应测试：`mvn -o test -Dtest=PolicyEngineTest#K11_两种联络方式都关被拒` → 绿；
5. 到 `POST /api/schema/validate` 发一张带 `Script` 组件的卡 → 返回非白名单拒绝；
6. 反过来：把本体里 K09 的 `min` 改成 20 → `POST /api/ontology/reload` → 再确认同一张卡 → 409。

**第 6 步是重点**：证明规则在数据里，不在代码的 if 里。
