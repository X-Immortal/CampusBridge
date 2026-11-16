# M1 · CampusHub 本体 V0.1

> 门槛：**≥15 概念 / ≥20 关系 / ≥10 约束**，每个概念要有定义、例子、反例。
> 课件出处：02 p70（五组资产）、02 p65（轻量本体结构）、02 p72（常见误区）。

**本体不是文档，是代码。** 唯一的真身是
[`backend/src/main/resources/ontology/campus-ontology.v1.json`](../backend/src/main/resources/ontology/campus-ontology.v1.json)，
本页由 `node scripts/gen-ontology-doc.mjs` 从它生成 —— 生成时间之外，任何手改都会在下次生成时被覆盖，
这本身就是「文档和代码不许长成两套」的保证。

| 项 | 值 |
|---|---|
| 版本 | 1.0.0 |
| 更新日期 | 2026-11-05 |
| 内容指纹 | `2074d2e7d90b0d85` |
| 概念 / 关系 / 约束 | **20 / 23 / 15** |
| 负责 | CampusBridge 2.0 · 需求与本体组（角色 A） |
| 运行时输出 | `GET /api/ontology`（含本页全部字段） |
| 现场热更新 | 改 JSON → `POST /api/ontology/reload`，不重启 |

加载时做硬校验：数量不达标、概念缺定义/例子/反例、关系引用了不存在的概念、
约束引用了没实现的求值器 —— **一律启动失败**，不允许带着半套本体上线
（`OntologyRegistry#validate`，有测试 `OntologyRegistryTest` 守着）。

---

## 一、概念（20 个）

| # | 概念 | 定义 | 例子 | 反例 | 负责 | 版本 |
|---|---|---|---|---|---|---|
| C01 | **User**（用户） | 在平台上有校园身份的求助者或帮助者；同一账号可以在两类角色之间切换，切换不改变身份认证与信用。 | 发布代取快递任务的大三学生；顺路接单的研究生 | 没有通过校园认证的游客账号（只能浏览，不能发布或接单） | A | 1.0 |
| C02 | **Goal**（目标） | 用户用自然语言表达的一次意图，还不是任务；必须经过澄清、补全约束后才可能变成 Task。 | 今晚帮我取三个快递 | 已经填好全部字段的发布表单（那是 Task 的输入，不是 Goal） | A | 1.0 |
| C03 | **Task**（任务） | 在平台上有明确时间、地点、报酬与完成标准，可被他人承接并产生业务事实的请求；状态由状态机守卫，不靠对话上下文判断。 | 状态 OPEN 的代取快递任务 | 聊天里一句“有人能帮忙吗”（没有结构化承诺，不能建单） | A | 1.0 |
| C04 | **TaskApplication**（接单申请） | 帮助者对某个 OPEN 任务提交的承接意向，只是候选，不等于接单成功；是否成立由发起人确认。 | 申请人留言“我顺路，今晚 8 点可以” | 任务已被别人接单后再次提交的申请（状态机应拒绝） | A | 1.0 |
| C05 | **CompletionProof**（完成凭证） | 履行方提交的、用于证明任务已完成的材料或描述，必须经求助方验收才产生完成事实。 | 快递柜照片 + “已放到 5 号楼门口” | 没有任何凭证就把任务直接改成 COMPLETED | A | 1.0 |
| C06 | **Review**（评价） | 任务完成后双方对彼此履约质量的评分与文字反馈，只对已完成任务开放。 | 5 分 + “很准时” | 任务还在履约中就打分的评价 | A | 1.0 |
| C07 | **CreditEvent**（信用事件） | 一次会改变用户信用或积分的确定性事件，必须携带 ruleId 与依据，能向用户解释“为什么扣分”。 | 超时未取件扣 5 分，ruleId=K-credit-timeout | 管理员凭印象调整积分（没有 ruleId，不可解释也不可复现） | A | 1.0 |
| C08 | **CampusVerification**（校园认证） | 证明用户是本校学生的记录，是发布与接单的前置条件，也是纠纷处理时的责任依据。 | 学号 + 学生证图片，人工审核通过 | 只填了一个昵称就视为已认证 | A | 1.0 |
| C09 | **Preference**（偏好） | 影响系统为该用户选择交互或方案的可撤回倾向；分声明偏好、推测偏好、临时上下文三类，推测偏好不得固化成标签。 | 声明偏好：所有任务优先站内消息 | “今天上课不能接电话”——这是一次性情境，不是长期偏好 | A | 1.0 |
| C10 | **Constraint**（约束） | 一条确定性、可单测、有编号的业务规则，在 Action 执行前求值，能直接改变动作的结果。 | K01 预算必须大于 0，否则拒绝发布 | 只写在 Prompt 里的“请不要乱填预算” | A | 1.0 |
| C11 | **TimeWindow**（时间窗） | 任务可执行的时间区间，必须有边界与时区；只有模糊说法的必须先澄清再建单。 | 今天 21:00–22:00（Asia/Shanghai） | “晚上”这种没有边界的描述 | A | 1.0 |
| C12 | **Location**（地点） | 有语义层级的校园地点（校区 / 楼 / 点），带隐私分级；隐私地点默认只对已确认的承接方披露。 | 北门菜鸟驿站（公开点）；5 号宿舍楼 502（隐私点） | “老地方”这种无法定位的说法 | A | 1.0 |
| C13 | **RiskLevel**（风险等级） | 由时间、物品价值、是否上门、是否涉及隐私位置决定的分级，决定是否需要风险披露与二次确认。 | 深夜上门取贵重物品 = HIGH | 白天在公开点取一份文件，却按 HIGH 要求披露（过度限制也是缺陷） | A | 1.0 |
| C14 | **Confirmation**（确认） | 用户对某个副作用 Action 的显式授权；绑定 taskId、action、任务版本、参数摘要、有效期与 nonce，任一变化即失效。 | 确认令牌 5 分钟内有效，只能用于 createTask(version=3) | 昨天生成的确认卡今天还能提交（任务版本已经变了） | A | 1.0 |
| C15 | **Reward**（报酬） | 任务发起人给履行方的回报，必须有形态与数量；形态来自本体，而不是前端下拉框里写死的字符串。 | CASH 8 元；NEGOTIABLE “请喝一杯咖啡” | CASH -5 元；CASH 8 美元 | A | 1.0 |
| C16 | **ModerationRule**（内容治理规则） | 发布前对文本的确定性检查，命中即拒绝并返回规则码；同一输入必须得到同一结果。 | CONTACT_LEAK 命中手机号，拒绝发布 | 靠模型感觉“这段话可能违规”就拒绝（不可解释、不可复现） | A | 1.0 |
| C17 | **ClarificationQuestion**（澄清问题） | 系统判断某槽位缺失或不确定时生成的结构化问题，必须说明为什么问、影响哪个动作。 | “最晚几点要取到？”——缺少 deadline，无法判断是否可接单 | 把用户没说的时间猜成“今晚 8 点”然后直接建单 | A | 1.0 |
| C18 | **Answer**（答案） | 用户对澄清问题的回答，带来源（用户声明 / 系统推测）与作用域（本次任务 / 长期偏好）。 | “21 点前都行”，作用域=本次任务 | 把一次性回答自动升级成永久偏好 | A | 1.0 |
| C19 | **UiCard**（界面卡片） | 由声明式 Schema 描述、只含白名单组件的交互单元；任务结束后卡片可以失效，但业务事实与授权记录必须保留。 | 确认卡：Summary + Button(requiresConfirmation=true) | 模型直接生成 ArkTS / JavaScript 在客户端执行 | A | 1.0 |
| C20 | **Action**（业务动作） | 会改变业务状态的命令（发布、接单、取消、完成、评价），必须经 Tool Gateway 鉴权、确认与审计。 | createTask / acceptTask / cancelTask | 客户端拿着数据库连接直接 UPDATE tasks | A | 1.0 |

> 读法：**反例那一列才是边界。** 比如 C09 Preference 的反例是「今天上课不能接电话」——
> 那是一次性情境，不是长期偏好。分不清这两者的系统，会把用户的临时处境固化成标签。

---

## 二、关系（23 条，全部有方向）

| # | 主体 | 谓词 | 客体 | 基数 | 说明 |
|---|---|---|---|---|---|
| R01 | User | **publishes** | Task | 1:N | 发起人发布任务 |
| R02 | Goal | **decomposes_into** | Task | 1:N | 一个目标可能拆成多个任务（取三个快递） |
| R03 | Task | **receives** | TaskApplication | 1:N | 一个任务可被多人申请 |
| R04 | User | **submits** | TaskApplication | 1:N | 帮助者提交申请 |
| R05 | User | **provides** | Task | 0..N | 帮助者承接任务后成为履行方 |
| R06 | Task | **requires** | CompletionProof | 0..1 | 需要凭证的任务在完成前必须有凭证 |
| R07 | Task | **receives** | Review | 0..N | 完成后双方各可评价一次 |
| R08 | Review | **awards** | CreditEvent | 0..1 | 评价产生信用事件 |
| R09 | CampusVerification | **verifies** | User | N:1 | 认证记录证明用户身份 |
| R10 | User | **declares** | Preference | 1:N | 用户可声明多条偏好，且可撤回 |
| R11 | Preference | **scopes** | Goal | N:1 | 偏好影响目标如何被理解与呈现 |
| R12 | Constraint | **constrains** | Task | N:1 | 约束决定任务能否发布 |
| R13 | TimeWindow | **bounds** | Task | 1:1 | 任务必须有可执行时间窗 |
| R14 | Location | **locates** | Task | 1:N | 取件点与送达点 |
| R15 | RiskLevel | **classifies** | Task | N:1 | 风险等级决定是否披露 |
| R16 | Confirmation | **authorizes** | Action | 1:1 | 一次确认只授权一个动作 |
| R17 | Action | **affects** | Task | N:1 | 动作改变任务状态 |
| R18 | ClarificationQuestion | **requires** | Answer | 0..1 | 问题等待回答 |
| R19 | Answer | **resolves** | ClarificationQuestion | N:1 | 回答解决某个问题 |
| R20 | UiCard | **renders** | Task | N:1 | 卡片渲染某个任务的快照 |
| R21 | UiCard | **binds** | Action | 1:N | 卡片按钮绑定动作，高风险动作必须带确认 |
| R22 | ModerationRule | **checks** | Task | N:N | 内容规则在发布前检查 |
| R23 | Reward | **compensates** | Task | 1:1 | 每个任务恰好一种报酬 |

> 规则：谓词必须是动词，禁止所有关系都叫「关联」。这里一共 21 种谓词。

---

## 三、约束（15 条，全部可单测）

| # | 约束 | 依据 | 级别 | 求值器 | 代码位置 | 正例 | 反例 |
|---|---|---|---|---|---|---|---|
| K01 | 预算必须大于 0，且金额不得为负 | 报酬是对履行的对价，负值或不存在的金额会让任务无法成立 | BLOCK | `reward.amount.positive` | `com.campusbridge.policy.ConstraintEvaluators#rewardAmountPositive` | CASH 8 元 | CASH -5 元 |
| K02 | 货币只能是 CNY | 校园场景统一计价，混用币种会让比较卡与预算判断失真 | BLOCK | `currency.allowed` | `com.campusbridge.policy.ConstraintEvaluators#currencyAllowed` | amount=8, currency=CNY | amount=8, currency=USD |
| K03 | 缺时间 / 地点 / 报酬时不得进入确认，只能继续澄清 | 这三项决定任务能否被履行，也决定卡片上能写什么 | BLOCK | `clarification.required` | `com.campusbridge.policy.ConstraintEvaluators#clarificationRequired` | 三个槽位齐全，可以出确认卡 | 只有“取快递”三个字就出确认卡 |
| K04 | 截止时间必须晚于当前时间 | 过去的截止时间会让任务一发布就过期 | BLOCK | `deadline.future` | `com.campusbridge.policy.ConstraintEvaluators#deadlineFuture` | 今天是 11-05，deadline 是 11-05 21:00 | deadline 是昨天 21:00 |
| K05 | 深夜任务（22:00–06:00）或上门任务必须做风险披露 | 时间和是否上门直接决定人身与物品风险 | BLOCK | `risk.disclosure` | `com.campusbridge.policy.ConstraintEvaluators#riskDisclosure` | 23:00 上门取件 + riskDisclosureConfirmed=true | 23:00 上门取件但从未提示风险 |
| K06 | 副作用动作必须有有效确认，推测偏好不能代替确认 | 模型只产出候选，系统决定能不能执行；用户授权必须显式 | BLOCK | `confirmation.required` | `com.campusbridge.policy.ConstraintEvaluators#confirmationRequired` | 用户点了确认按钮，令牌有效 | 系统按“他上次也同意”自动发布 |
| K07 | 用户声明不想暴露宿舍号时，不得共享精确宿舍位置 | 隐私约束优先于匹配便利；这条来自 M4 现场挑战原题 | BLOCK | `privacy.dorm` | `com.campusbridge.policy.ConstraintEvaluators#privacyDorm` | shareDormRoom=false，卡片只显示“5 号楼附近” | 用户说“别告诉陌生人宿舍号”，任务里仍写着 502 |
| K08 | 同一幂等键只产生一次业务事实 | 网络重试与重复点击不能变成两张订单 | BLOCK | `idempotency.unique` | `com.campusbridge.tool.ToolGateway#execute` | 同一个 idempotencyKey 第二次提交返回第一次的 taskId | 同一次确认被处理两次，建出两张任务 |
| K09 | 任务标题长度 5–100 字 | 太短无法检索，太长在列表与卡片上都显示不全 | BLOCK | `title.length` | `com.campusbridge.policy.ConstraintEvaluators#titleLength` | “帮取三个快递到 5 号楼” | “取” |
| K10 | 完成标准必填且不少于 10 字 | 没有验收标准的任务必然在“算不算完成”上扯皮 | BLOCK | `completion.standard` | `com.campusbridge.policy.ConstraintEvaluators#completionStandard` | “快递放到 5 号楼一层货架并拍照” | “送到就行” |
| K11 | 关闭站内联络时必须打开线下联络方式 | 两个开关都关掉，双方根本无法碰面 | BLOCK | `contact.policy` | `com.campusbridge.policy.ConstraintEvaluators#contactPolicy` | onlyInAppContact=false, allowOfflineContact=true | onlyInAppContact=false, allowOfflineContact=false |
| K12 | 报酬形态必须在报酬类型集合内 | 本体定义的四类报酬之外的值会让结算与展示都没有依据 | BLOCK | `reward.type.allowed` | `com.campusbridge.policy.ConstraintEvaluators#rewardTypeAllowed` | rewardType=NEGOTIABLE, description=“请喝一杯咖啡” | rewardType=COFFEE |
| K13 | 确认令牌里的任务版本必须等于当前版本 | 任务被改过一次之后，旧确认卡不能再代表用户此刻看到的内容 | BLOCK | `version.increment` | `com.campusbridge.tool.TokenService#verify` | 令牌绑定 version=3，当前也是 3 | 令牌绑定 version=2，用户已经改到 4 |
| K14 | 单次代取件数不得超过 5 件 | 超过 5 件已经不是顺路，而是搬运工作，风险与报酬都不同 | BLOCK | `items.max` | `com.campusbridge.policy.ConstraintEvaluators#itemsMax` | 3 个快递 | 12 个快递 |
| K15 | 推测偏好参与高风险动作时必须再次确认 | 推测不是用户说的；越私密、越高风险，越不能替用户做主 | BLOCK | `preference.inferred.confirmation` | `com.campusbridge.policy.ConstraintEvaluators#inferredPreferenceConfirmation` | 推测“喜欢熟人优先”，在确认卡上标了“推测”并要求点确认 | 按推测的偏好直接把宿舍号发给陌生人 |

### 分阶段求值

「还在澄清」和「要产生副作用了」不是一回事：依赖确认令牌和幂等键的约束只在确认阶段跑，
否则用户在澄清时就会被误报「没有确认」。这条规则写在 JSON 的 `phases` 字段里。

| # | 约束 | 生效阶段 |
|---|---|---|
| K06 | 副作用动作必须有有效确认，推测偏好不能代替确认 | CONFIRM |
| K08 | 同一幂等键只产生一次业务事实 | CONFIRM |
| K13 | 确认令牌里的任务版本必须等于当前版本 | CONFIRM |
| K15 | 推测偏好参与高风险动作时必须再次确认 | CONFIRM |

### 求值器一览

每条约束对应 `com.campusbridge.policy.ConstraintEvaluators` 里的一个确定性方法：
同一条草稿 + 同一个上下文，必须给同一个结果。**不做数据库访问、不调用模型、不读时钟以外的东西。**

---

## 四、状态机

### 4.1 对话会话（M1）

```
CLARIFYING ──补全所有必填──► PROPOSING ──选定方案──► AWAITING_CONFIRMATION
     │                          │                          │
     └────── 用户撤销 ──────────┴──────────────────────────┴──► CANCELLED
                                                                │
                                     确认 + 策略通过 ───────────┴──► CREATED
```

| 状态 | 含义 | 允许的动作 | 禁止的事 |
|---|---|---|---|
| CLARIFYING | 还缺必填信息 | answer / cancel | 出确认卡、建单 |
| PROPOSING | 信息齐了，在比较方案 | select / answer（改内容）/ cancel | 没有令牌就建单 |
| AWAITING_CONFIRMATION | 已出确认卡，等用户点 | confirm / edit / cancel | 用旧版本令牌建单 |
| CREATED | 订单已落库 | open | 重复建单（靠幂等键挡住） |
| CANCELLED | 用户放弃 | — | 之后再确认 |

### 4.2 业务任务状态（沿用 1.0）

`DRAFT → OPEN → ACCEPTED → IN_PROGRESS → PENDING_CONFIRMATION → COMPLETED → REVIEWED`，
另有 `CANCELLED / DISPUTED / REJECTED` 三个出口。

**2.0 没有改这套状态机**，只是在它前面加了一层「草稿 + 确认」：
用户看到的 CREATED 对应业务里的 OPEN，中间那层是 2.0 新增的候选态。

---

## 五、本体 → 代码 → 事件 → 卡片 → 工具（至少两个概念全链路打通）

| 概念 | 代码类型 | 领域事件 | 卡片出现位置 | 工具 / 命令 |
|---|---|---|---|---|
| **C03 Task** | `domain/TaskDraft` + 1.0 `pojo/Task` | `understanding.applied`、`task.created` | 四类卡片都渲染它的快照 | `ToolGateway` → `TaskService.createTask` |
| **C09 Preference** | `TaskDraft.Source.INFERRED` + 约束 K15 | `understanding.applied`（declared / inferred 计数） | 确认卡 Summary 的「系统推测」标记 | 推测值不能替代确认 |
| **C14 Confirmation** | `tool/ConfirmToken` | `confirmation.issued`、`confirm.requested` | 确认卡里的 `token` | `TokenService.verify/consume` |
| **C15 Reward** | `DraftField.REWARD_*` | — | Summary 的「报酬 / 金额」行 | `ServiceTaskCommandAdapter` 映射成 1.0 枚举 |
| **C17 ClarificationQuestion** | `domain/ClarificationQuestion` | `clarify.asked`、`clarify.answered` | 澄清卡的 `Field` 块 | `ClarificationPolicy` 决定问什么 |

---

## 六、现场怎么证明「本体真的在起作用」

两步，三十秒（完整脚本见 `docs/M1-demo.md`）：

1. **改一条约束**：把 `K09` 的 `params.min` 从 5 改成 20；
2. **刷新本体**：`POST /api/ontology/reload`（不重启）；
3. **再确认同一张卡**：返回 409，`violations` 里是
   `K09：标题要 20–100 字（当前 10 字）`。

反过来把 JSON 改回去再 reload，同一张卡就又能建单 —— 这说明**改的是规则，不是代码里的 if**。

---

## 七、常见误区自查（课件 02 p72，逐条打勾）

- [x] 不是只有名词列表：20 个概念每个都有定义、例子、反例
- [x] 不是照抄数据库表：概念里有 `Goal`、`Preference`、`RiskLevel`、`ClarificationQuestion` 这些表里没有的东西
- [x] 概念粒度没混乱：「用户 / 注册页面 / 代取按钮」不在同一层，`UiCard` 与 `Action` 单独成概念
- [x] 关系不是全叫「关联」：21 种谓词，全部有方向
- [x] 本体没停在文档里：约束由 `PolicyEngine` 求值，直接决定 `ToolGateway` 是否执行
- [x] 没有让模型自由改本体：JSON 是唯一来源，加载与热更新都过校验，改坏了启动/刷新直接失败

## 八、硬指标计数

| 项 | 要求 | 当前 | 达标 |
|---|---|---|---|
| 概念 | ≥15 | 20 | ☑ |
| 关系 | ≥20 | 23 | ☑ |
| 约束 | ≥10 | 15 | ☑ |
| 涉及 AI / 个性化的约束 | ≥2 | K03 / K06 / K15 | ☑ |
| 全链路映射的概念 | ≥2 | C03 / C09 / C14 / C15 / C17 | ☑ |
| 每条约束有正例 + 反例 | 全部 | 全部（加载时校验） | ☑ |
