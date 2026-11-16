# evals/ —— 固定评测集与执行器

一条样本一个 JSON，放在 `cases/`。文件名随意，但**前缀用 `M1-` / `M4-` 区分阶段**。

```powershell
node run.mjs --dry-run                  # 只校验样本格式 + 打印八类覆盖矩阵
node run.mjs --dry-run --min 15         # M1 阶段门槛是 15 条（M4 要 40 条）
node run.mjs --live http://127.0.0.1:8081
```

`--live` 会真的打服务，逐条检查 `check`，把结果写成 `reports/run-<时间戳>.json`。
**评审那天现场跑的就是这一句**，不是 PPT 里的截图。

---

## 一条样本长什么样

```jsonc
{
  "id": "M1-006",
  "category": "正常",                    // 必须是下面八类之一
  "goal": "今晚九点去北门菜鸟驿站帮我取3个快递，预算8元",
  "context": { "requesterId": 1 },       // 可选：persona / modelMode / model / requesterId
  "flow": {                              // 可选：一直跑到建单或取消
    "answers": [{ "field": "deadline", "value": "2026-11-05T21:00" }],
    "select": "now",
    "confirm": true,
    "cancel": false,
    "idempotencyKey": "eval-M1-006"
  },
  "expect": { "cards": ["comparison", "confirmation"] },  // 给人看的意图说明
  "check": {                             // 给机器看的判据
    "cardKindIn": ["comparison"],
    "questionFieldsInclude": ["deadline", "location"],
    "degraded": false,
    "taskIdNull": true,
    "finalOutcome": "OK",
    "taskIdNotNull": true,
    "stateIn": ["CREATED"],
    "violationsInclude": ["K13"],
    "validSchema": false,
    "errorsInclude": ["非白名单组件被拒绝：Script"]
  },
  "note": "为什么有这条样本"
}
```

### 八类必须都覆盖

正常 / 模糊 / 信息缺失 / 特殊情境 / 恶意输入与越权 / 错误Schema / 工具失败 / 模型超时。

M1 阶段共 18 条（门槛 15）；2026-11-16 已扩到 **40 条**（八类分别 5/4/5/6/5/5/5/5），现场跑 `--live` 40/40。

---

## 三类样本

| 类型 | 用哪个字段 | 打哪个端点 |
|---|---|---|
| 对话样本 | `goal` | `POST /api/assistant` |
| 全流程样本 | `goal` + `flow` | 追加 answers / select / confirm / cancel |
| Schema 样本 | `schema` | `POST /api/schema/validate` |

## `check` 支持哪些判据

| 判据 | 含义 |
|---|---|
| `cardKindIn` | 首张卡片类型在列表里（clarification / comparison / confirmation / progress） |
| `questionFieldsInclude` | 这次追问的字段里包含这些 |
| `degraded` | 是否降级（模型不可用时必须是 `true`） |
| `taskIdNull` / `taskIdNotNull` | 有没有产生业务事实 |
| `finalOutcome` | `OK` / `DENIED` |
| `stateIn` | 最终会话状态 |
| `violationsInclude` | 被哪几条约束拦住（`K01`…`K15`） |
| `validSchema` / `errorsInclude` | Schema 校验结果 |

## 纪律

- **一条样本只测一件事**，失败时能一眼看出是哪个能力坏了。
- **`expect` 写给人和老师看，`check` 写给机器看**；两者不一致说明样本该改了。
- 失败的样本不要删，挪到 `docs/failures.md` 记一笔「为什么还没修」——M4 会回来收。

## 接口约定

`POST /api/assistant` 请求体 `{ goal, context }`；响应体至少包含
`{ sessionId, state, card, unknowns, assumptions, modelMode, degraded, taskId, violations, outcome }`。
完整流程见 `backend/src/main/java/com/campusbridge/controller/AssistantController.java`。
