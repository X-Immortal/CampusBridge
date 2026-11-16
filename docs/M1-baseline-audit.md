# M1 · CampusBridge 1.0 接管审计

> 课件要求：「先运行和审计……再决定如何演化」「不得为了方便完全重写 CampusHub 1.0；必须展示旧语义如何被发现、保留、修正或迁移。」（02 p68）
> **这份文档就是"我们发现旧语义"的证据。** 填完它，M1 的"本体 V0.1"才有出处，而不是凭空编 15 个概念。

审计对象：上学期 CampusBridge 1.0（`backend/` + `frontend/`，192 次提交，143 个测试全绿）。

---

## 一、课件点名的五个问题，逐个回答

### Q1 现有需求分类是否硬编码？

**是，而且是枚举级的硬编码。**

| 位置 | 内容 | 后果 |
|---|---|---|
| `dto/enums/ApiEnums.java` | `TaskCategory { ERRAND, AGENCY }` | 只有"跑腿/代办"两类。想要"组队搬运""同行"必须改枚举 + 改前端 + 发版 |
| 同上 | `RewardType { FREE, POINTS, CASH, NEGOTIABLE }` | 报酬形态固定四种，"请对方喝咖啡"这种（课件 M4 现场题原话）**表达不出来** |
| 同上 | `PostCategory { INQUIRY_HELP, SECOND_HAND, TEAM_FRIEND, CAMPUS_SOCIAL }` | 同上 |
| `pojo/Task.java:18` | `private String category;` | 枚举在，实体却用 String —— 编译期不设防，能写入任意值 |

**2.0 决定**：分类从 Java 枚举迁到**本体**（`ontology/`），加新类只改数据不改代码；枚举保留为"内置类"的快速路径。

### Q2 订单状态由谁修改、是否可追溯？

**入口唯一，但类型不安全、追溯不全。**

- 唯一入口：`TaskServiceImpl.transitionTaskStatus(...)`（`TaskServiceImpl.java:446`）
- 动作枚举：`TaskStatusTransitionAction { START, CONFIRM_COMPLETION, REJECT_COMPLETION, DISPUTE, CANCEL }`
- 状态：`DRAFT / OPEN / PENDING_REVIEW / ACCEPTED / IN_PROGRESS / PENDING_CONFIRMATION / COMPLETED / REVIEWED / CANCELLED / DISPUTED / REJECTED`

**发现的问题**：

| # | 问题 | 证据 | 2.0 怎么改 |
|---|---|---|---|
| 1 | `Task.status` 是 **String**，不是枚举 | `pojo/Task.java:41` | 状态机 + 类型守卫，非法迁移在域层拒绝 |
| 2 | 没有 `version` 字段 | `pojo/Task.java` 全字段 | 加乐观锁版本，旧确认卡必须失效 |
| 3 | 历史表有（`TaskHistory`），但记的是"状态变化"，不是"谁授权了什么" | `service/impl/TaskHistoryServiceImpl.java` | 新增**确认令牌**与审计事件，区分"状态变了"和"谁批准了" |
| 4 | `TaskStatusTransitionAction` 里没有 `ACCEPT` | 枚举清单 | "接单"走的是 `TaskApplicationServiceImpl`，不在同一个状态机动线上 —— 2.0 要统一 |

### Q3 "完成""取消""信用"分别意味着什么？

| 词 | 1.0 的实现 | 含糊之处 | 2.0 必须说清 |
|---|---|---|---|
| 完成 | `COMPLETED` → `REVIEWED` 两个状态；`CompletionServiceImpl.confirmCompletion` 确认完成、`rejectCompletion` 驳回 | "提交凭证"和"确认完成"是两个人做的，但状态机没区分"待确认"和"已提交凭证" | 拆成 `履约中 → 待确认 → 已完成`，凭证是独立实体 |
| 取消 | `TaskStatus.CANCELLED` + `TaskServiceImpl.cancelTask/cancelTask(reason)` + `withdrawFromTask` | 发起人取消 vs 接单方退出，状态都落到 CANCELLED，事后分不清 | 区分 `Cancel` 与 `Withdraw`（课件 03 p66：Undo / Cancel / Compensate / Handoff 是四件事） |
| 信用 | `CreditServiceImpl` 积分增减 + `UserCredit` 等级 + `CreditRecord` 流水 | 扣分规则写在 service 里，页面看不到"为什么扣" | 信用规则进本体，每次扣分带 `ruleId` + 依据，可解释 |

### Q4 哪些规则只存在于页面或开发者脑中？

| 规则 | 现在在哪 | 问题 |
|---|---|---|
| 违规关键词 / 站外联系方式拦截 | `ContentModerationServiceImpl` + 规则码（`CONTACT_LEAK`、`PROHIBITED_KEYWORD`） | 规则码是字符串常量，不可枚举、不可版本化、不告诉用户依据 |
| 完成凭证是否必传 | `Task.requiresProof`（`Integer` 0/1） | 没有"为什么"和"什么算合格凭证" |
| 敏感任务风险披露 | `Task.riskDisclosureConfirmed`、`sensitiveReason`（都是字段） | 只在页面弹窗里用，域层不校验"高风险必须有披露" |
| 匿名/隐私联络 | `onlyInAppContact`、`allowOfflineContact`（`Integer`） | **这是宝藏**：1.0 已经有隐私联络语义，2.0 直接复用，不用重新发明 |
| 超时自动流转 | `TaskTimeoutServiceImpl` | 阈值在配置里（`task.timeout.*`），页面上不可见 |

**2.0 决定**：全部抽成 `Policy`，每条有 `ruleId`、有正例反例、能单测、能返回"为什么"。

### Q5 哪些用户行为和失败原因没有记录？

| 缺失 | 后果 | 2.0 补什么 |
|---|---|---|
| 用户怎么描述需求的原话 | 无法分析"为什么发布失败" | `goal.expressed` 事件 |
| 发到一半放弃 | 只剩一个 DRAFT，不知道为什么放弃 | `task.abandoned` + `atStage` |
| 内容被拦截后的用户反应 | 只记了 block，没记用户怎么改 | `moderation.blocked` → `moderation.revised` |
| 任何 AI 交互 | 1.0 里根本没有 | 全套 `intent.*` / `clarify.*` / `schema.*` / `tool.*`（见 `M4-data.md`） |
| 确认动作的主体 | 只有状态变更 | `confirmation.granted` + `nonce` + `taskVersion` |

---

## 二、保留 / 改造 / 新增

### 保留（一行都不动，2.0 直接站上去）

| 模块 | 为什么保留 |
|---|---|
| `pojo/` 全部实体（19 个） | 业务语义真实且够用，重写只会丢东西 |
| `mapper/` + `resources/**/*.xml`（20 组 MyBatis） | SQL 是资产 |
| `AuthServiceImpl` + `JwtAuthFilter` + `SecurityConfig` | 认证鉴权已经在跑，M3 的"身份由后端注入、模型不能指定 userId"天然满足 |
| `TaskApplicationServiceImpl`（接单/候选/自动拒绝其余） | 这是 1.0 最有价值的业务规则之一 |
| `CompletionServiceImpl`（凭证、确认、驳回、争议） | 同上 |
| `ContentModerationServiceImpl` | 直接变成一条 Policy |
| 143 个测试 | **不许退化**，2.0 的 CI 必须保持全绿 |

### 改造（改语义，不推倒）

| 改造点 | 从 | 到 |
|---|---|---|
| 状态类型 | `String status` | 枚举 + 状态机守卫，非法迁移在域层拒绝 |
| 并发控制 | 无版本 | 加 `version`，写前校验 |
| 分类 | Java 枚举硬编码 | 本体驱动，枚举降级为"内置类" |
| 规则 | 散在 Service | 独立 Policy，带 `ruleId` 和依据 |
| 完成/取消 | 状态混在一起 | 拆成履约、凭证、确认、取消、退出五件事 |
| 信用 | service 内计算 | 规则入本体，扣分带依据 |

### 新增（1.0 完全没有的）

| 新增 | 放哪 | 对应里程碑 |
|---|---|---|
| Agent Orchestrator（理解 → 澄清 → 提案） | `backend/.../agent/` | M1 / M2 |
| Model Gateway（路由、配额、超时、熔断、降级） | `backend/.../gateway/model/` | M3 |
| Tool Gateway（白名单、确认令牌、幂等、审计） | `backend/.../gateway/tool/` | M2 / M3 |
| 本体 + PolicyEngine | `backend/.../ontology/` | M1 |
| UI Schema 生成与校验 | `backend/.../schema/` + `app/` 的 Renderer | M2 |
| 交互事件表 | `backend/.../events/` + 数据库表 | M4 |
| 鸿蒙客户端 | `app/` | M2 |
| 评测集 | `evals/` | M1 / M4 |

---

## 三、2.0 的系统图（在我们自己的项目上）

```
鸿蒙 app（新建）            Vue frontend（保留，L2 降级固定页面）
        │                            │
        └──────────┬─────────────────┘
                   ▼
        Spring Boot backend（1.0 保留）
        ├─ agent/          Orchestrator        ← 新增
        ├─ gateway/model/  Model Gateway       ← 新增
        ├─ schema/         UI Schema 生成      ← 新增
        ├─ ontology/       本体 + PolicyEngine  ← 新增
        ├─ gateway/tool/   Tool Gateway        ← 新增
        ├─ controller/     （1.0 原样）         ← 保留
        ├─ service/        （1.0 原样，逐步加守卫）← 保留改造
        └─ mapper/         （1.0 原样）         ← 保留
                   ▼
              MySQL（1.0 schema + 事件表 + 版本列）
```

**一句话讲清楚**：1.0 的业务内核不动，外面套一层"能说话的皮"和一层"能收口的闸"。

---

## 四、审计自查

- [ ] 五个问题都有具体证据（文件:行号），不是"大概是这样"
- [ ] "保留"清单里每一项都写明了为什么不能动
- [ ] 至少说出 3 条 1.0 已有的、我们不该重新发明的东西（提示：`onlyInAppContact`、`requiresProof`、`riskDisclosureConfirmed` 就是三条）
- [ ] 143 个测试在 2.0 里仍然全绿
