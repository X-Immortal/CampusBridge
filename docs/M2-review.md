# M2 审阅文档 · 契约、流式协议与鸿蒙客户端

> 审阅对象：`review/complete-2026-11-23` 分支上的 M2 资产。
> 一句话结论：**契约、五类流事件、四类卡片、白名单校验、断线重连、鸿蒙客户端和 9 条测试映射都在；2 条测试（冷启动恢复、投影裁剪）仍是 ◐；`M2-schema.md` / `M2-protocol.md` 有过期段落，以 `contract/src/**` 和本文件为准。**
>
> 本文件覆盖“当时 M2 交付”与“11/23 新增 App Shell/任务页后的现状”，两者会分开写。

---

## 1. 一页结论

| 项 | 结果 |
|---|---|
| 契约测试 | **23/23**（contract 6 + domain 8 + transport 9） |
| 契约文件 | `types.ts`、`whitelist.ts`、`events.ts`、`sse.ts` 四个源 + 6 个 fixtures |
| 流式协议 | v1：两条通道、五种 kind、SSE 帧、`after` 游标、去重/缺口/恢复 |
| 四类卡片 | clarification / comparison / confirmation / progress |
| 白名单 | 5 种 block、7 种 action（副作用只有 `confirm`）、23 个草稿字段 |
| 服务端流 | `GET /api/stream` 回放 14 条事件、五种 kind 齐全、`stream.end` 收尾 |
| 鸿蒙客户端 | API 22；HAP **299 KB，BUILD SUCCESSFUL** |
| 页面 | 四卡演示 `Index`、真实链路 `Assistant`、Shell、任务列表、任务详情 |
| 评测 | **40/40** live，含 3 条流断言（M1-006/016/019） |
| 九条最小测试 | **7 ✅ / 2 ◐**（⑥⑦） |
| 证据 | M2 六张模拟器截图 + M3 新增 Shell/任务页布局树 |

---

## 2. 干了什么（按交付物）

### 2.1 契约先行

- `contract/src/protocol/types.ts`：`UiCard` / `CardBlock` / `CardAction` / `TokenView` / `AssistantResponse` 等；ArkTS 兼容约束：不用 `any`、`enum`、`Record`、索引签名。
- `contract/src/protocol/whitelist.ts`：`ALLOWED_BLOCKS` 5 种、`ALLOWED_ACTIONS` 7 种、`SIDE_EFFECT_ACTIONS = ['confirm']`、23 个草稿字段。
- `contract/src/transport/events.ts`：流事件信封与五种 kind。
- `contract/src/transport/sse.ts`：SSE 帧编解码。
- 6 个 fixtures：四类卡、坏卡、示例流。
- `contract/tools/mock/server.mjs`：mock SSE，5 种事件 + `stale` / `tool_fail` / `drop` / `model_down` 四种故障。
- 同步：`scripts/sync-contract.ps1` 把四个文件复制到 `app/.../protocol` 与 `app/.../transport`；**app 里这四个文件是副本，不许手改**。

### 2.2 协议 v1

- 文字版：`contract/PROTOCOL.md`
- 两条通道：
  - 同步动作：`POST /api/assistant`、`/answers`、`/select`、`/confirm`、`/cancel`；只有 confirm 成功才写库。
  - 只推送：`GET /api/stream?sessionId=&after=`；不产生业务事实。
- 五种 kind：`text.delta`、`schema.card`、`tool.progress`、`task.snapshot`、`stream.end`。
- SSE 帧：空行结束；`id:` 只是重连提示，协议判定用 JSON 里的 `eventId` / `sequence`。
- 去重/缺口/恢复：`eventId` 去重、`sequence` 找缺口、`after=N` 只发 `>N`、`cursor_expired` 全量刷新。
- 硬规则：`schemaVersion !== "1.0"` 拒绝并降级；`blocks[].type` 不在白名单整张卡拒绝；`Field.field` 不存在拒绝；副作用动作缺 `requiresConfirmation` 拒绝。

### 2.3 服务端流实现

- `events/StreamEvent.java` 信封、`events/StreamEventLog.java` 每会话序号从 1、`controller/StreamController.java` 的 `SseEmitter`（150ms 轮询，`after` 游标）。
- 测试：`StreamControllerIntegrationTest`（回放 + after 游标）、`StreamEventLogTest`。
- 冒烟：`scripts/smoke-assistant.mjs`，输出 `STREAM 200 events=14`、五种 kind 齐全。
- 2026-11-16 实测：14 条事件、序号 1–14 连续、`stream.end` 后控制器收尾。

### 2.4 鸿蒙客户端

| 文件 | 作用 |
|---|---|
| `pages/Index.ets` | 四类卡片演示 + 坏卡拒绝 + 七种流事件计数 |
| `pages/Assistant.ets` | 真实链路：`/api/assistant` → 追问 → 选择 → 确认 → `/api/stream` 回放 |
| `renderer/CardRenderer.ets` | 声明式卡片渲染，只认白名单 block |
| `transport/sseParser.ts` | UTF-8 分块、SSE 分帧、`StreamAssembler` 去重/缺口 |
| `protocol/*` | 契约副本（四个文件，禁止手改） |
| `fixtures/Fixtures.ets` | 四类卡与坏卡的演示数据 |
| `pages/Shell.ets`（11/23 新增） | 底部导航（助手/任务）、后端探针、L2 降级横条、平板 master-detail |
| `pages/TaskList.ets` / `TaskDetail.ets`（新增） | 任务列表/详情接 1.0 公开接口；手机列表→详情 |
| `components/TaskListPanel.ets` / `TaskDetailPanel.ets`（新增） | 列表与详情的可复用 UI |
| `ui/cardGuard.ts`（新增） | 客户端白名单：坏卡整张拒绝、保留上一张 |
| `ui/taskApi.ts`（新增） | `GET /api/v1/tasks`、`GET /api/v1/tasks/{id}` 只读封装 |

要点：
- 模型只产出**声明式 Schema**，客户端绝不执行模型生成的 ArkTS/JS/SQL。
- 确认卡带令牌；旧版本卡因 `taskVersion` 失效。
- 流断线按 `after` 重连；重复 `eventId` 不重复渲染。
- L2：Shell 探针失败显示横条、助手按钮禁用；任务页可重试/保留已加载数据。
- 平板：`≥840vp` 左列表右详情（同一套 `TaskListPanel` / `TaskDetailPanel`）。

### 2.5 评测与测试

- `evals/run.mjs` 支持 `stream: true`，断言 `streamKindsInclude` / `streamMinEvents` / `streamEndReason`。
- 挂上的样本：`M1-006`、`M1-019`（`done`）、`M1-016`（`cancelled`）。
- 2026-11-23 实测：**40/40**，含 3 条流检查。
- 九条最小测试映射：`docs/M2-tests.md`，7 条 ✅、2 条 ◐（⑥冷启动恢复、⑦投影裁剪），每行都能点到真实测试文件。

---

## 3. 当时 vs 现在

| 指标 | M2 当时（11/16 文档/截图） | 当前分支（11/23） |
|---|---|---|
| 契约测试 | 23/23 | **23/23** |
| 评测 | 40/40（含 3 条流断言） | **40/40** |
| HAP | 161 KB（M2 首版） | **299 KB**（加 Shell/任务页） |
| 页面 | Index + Assistant | **Shell + Index + Assistant + TaskList + TaskDetail** |
| 白名单 | 服务端 + Index 演示 | 服务端 + `ui/cardGuard.ts` 客户端拒绝，坏卡保留上一张 |
| 降级 | Assistant 的 `degraded` 字段 | Shell 顶部 L2 横条 + 助手禁用 + 任务页可重试 |
| 任务视图 | 无 | 列表、详情、平板 master-detail（≥840vp） |
| 虚拟屏证据 | 6 张 M2 截图 | 新增 00–04 布局树（截图接口本轮不可用） |

---

## 4. 证据索引

| 想验证什么 | 看哪里 |
|---|---|
| 契约与协议 | `contract/PROTOCOL.md`、`contract/src/**` |
| 四类卡片 fixtures | `contract/fixtures/card-*.json` |
| 白名单与坏卡 | `contract/fixtures/bad-card-script.json`、`contract/test/contract.test.ts` |
| 流解析/补放 | `contract/test/transport.test.ts` |
| 服务端流 | `StreamControllerIntegrationTest`、`scripts/smoke-assistant.mjs` |
| 四卡/坏卡截图 | `evidence/M2/screenshots/01–05` |
| 真实链路截图 | `evidence/M2/screenshots/06-real-link-e2e.png` |
| Shell/L2/任务页 | `evidence/M3/00–04-*.json`、`evidence/M3/README.md` |
| 九条测试映射 | `docs/M2-tests.md` |
| HAP 构建 | `powershell -File scripts/build-app.ps1` |

复跑命令：

```powershell
cd contract; npm run check
cd ..\evals; node run.mjs --live http://127.0.0.1:8081
powershell -ExecutionPolicy Bypass -File ..\scripts\build-app.ps1
```

---

## 5. 文档债与已知缺口

| 项 | 说明 | 处理 |
|---|---|---|
| `docs/M2-schema.md` 是早期规范稿 | 写了 `Button` block、`Field.source`、`actionId: publish`，这些**不在当前契约**；§三“≥2 类任务视图”表是空的 | 以 `contract/src/**` 为准；新 Shell/List/Detail 已补上视图，文档待重写 |
| `docs/M2-protocol.md` §7 写“Assistant 尚未在模拟器跑过” | 11/16 已跑通并留截图 06；11/23 Shell 也实测 | 下次改文档时删掉这句 |
| ⑥ 刷新后恢复 `lastStableVersion` | 服务端有 `GET session` 快照，客户端没有冷启动 hydrate | ◐；`DraftStore.restore` + 冷启动拉 session |
| ⑦ 投影自动移除禁止动作 | 当前是整卡拒绝，不是裁剪 | ◐；加 `project(card, viewer)` 再校验 |
| 平板 master-detail | 代码已编过，没有平板模拟器实拍 | 用 DevEco 平板 API22 起一次，补截图 |
| 流实现 | 150ms 轮询 `SseEmitter`，不是事件驱动 push；`StreamEventLog` 在内存 | 演示够用；压测/重启恢复需换事件驱动 + 持久化 |
| 四卡演示数据 | `Index` 用 `fixtures/Fixtures.ets`，不是每条都来自服务端 | 真实链路看 `Assistant`；答辩时讲清两者区别 |
| 新页面截图 | 模拟器 `snapshot_display` / `screenCap` 报 `Failed to get display pixelMap` | 用 `uitest dumpLayout` 布局树作证据；换设备补图 |

---

## 6. 现场可能被问的三题

1. **“模型生成的 UI 会不会执行代码？”**
   不会。模型只能产出 `UiCard` 的 JSON；客户端只渲染 5 种白名单 block；`Script`/未知字段/缺确认的 `confirm` 整张卡拒绝。`POST /api/schema/validate` 与 `SchemaValidatorTest` 都能现场打。
2. **“断线了怎么办？”**
   客户端记 `lastSequence`；重连 `?after=<lastSequence>`；同一 `eventId` 去重；跳号判缺口；`cursor_expired` 全量刷新。`transport.test.ts` 有逐字符、1 字节块、多行 data、UTF-8 切分等测试。
3. **“旧确认卡还能用吗？”**
   不能。令牌绑定 `taskVersion` + 参数摘要；改了合法字段后旧卡 `409 K13`。`AgentFlowTest#改过内容之后旧确认卡失效` 现场可跑。

---

## 7. 审阅顺序（30 分钟版）

1. `contract/PROTOCOL.md`：两条通道、五种 kind、`after`、去重/缺口。
2. `contract/src/protocol/whitelist.ts` + `types.ts`：白名单到底限制了什么。
3. `docs/M2-tests.md`：九条测试 7✅/2◐，先接受边界。
4. `app/entry/src/main/ets/pages/Shell.ets`：底部导航、L2、平板断点。
5. `app/entry/src/main/ets/pages/Assistant.ets`：真实链路 + 坏卡拒绝。
6. `docs/M2-protocol.md`：本仓库实际实现了什么（§7 的过时句忽略）。
7. `evidence/M2/README.md` + `evidence/M3/README.md`：截图与布局树能不能对上。
