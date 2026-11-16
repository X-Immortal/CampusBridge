# M2 · 协议与客户端接入（实现说明）

> 契约文字版是 [`contract/PROTOCOL.md`](../../contract/PROTOCOL.md)，类型以 `contract/src/**` 为准。
> 本文只记录**本仓库已经实现的东西**：端点、状态、事件从哪来、怎么验。

---

## 1. 两条通道

| 通道 | 端点 | 作用 | 是否产生业务事实 |
|---|---|---|---|
| 同步动作 | `POST /api/assistant`、`/sessions/{id}/answers`、`/select`、`/confirm`、`/cancel` | 开始、回答、选方案、确认、撤销 | **只有 confirm 成功才写库** |
| 只推送 | `GET /api/stream?sessionId=&after=` | 文本片段、卡片、工具进度、任务快照、结束 | 否 |

客户端不能靠"读到某段文本"去建单；建单只认 `POST .../confirm` 的返回和 `task.snapshot`。

## 2. 状态机（窄状态机，不能跳步）

```
CLARIFYING ──补全──> PROPOSING ──select──> AWAITING_CONFIRMATION ──confirm──> CREATED
      │                  │                        │
      └──────────── cancel ──────────────────────┴──> CANCELLED
```

`AgentOrchestrator` 保证：缺槽位只能停在 `CLARIFYING`；没 `select` 不能确认；`confirm` 被拒时退回 `AWAITING_CONFIRMATION` 而不是 `CREATED`。

## 3. 五种流事件从哪来

| kind | 产生位置 | 说明 |
|---|---|---|
| `text.delta` | `AgentOrchestrator.emit` | 一句助理气泡文案（按卡片类型） |
| `schema.card` | 同上 | 完整 `UiCard`，客户端先过白名单再渲染 |
| `tool.progress` | `confirm` 前后 | `started` → `done` / `failed`，工具名 `createTask` |
| `task.snapshot` | confirm 成功 | `taskId + taskStatus + taskVersion`，以服务端为准 |
| `stream.end` | 会话终态 | `done` / `cancelled`；只有终态才发，澄清阶段流保持打开 |

实现：`events/StreamEvent.java`（信封）、`events/StreamEventLog.java`（每会话序号从 1 开始）、`controller/StreamController.java`（`SseEmitter`，150 ms 轮询，`after` 游标）。

## 4. 去重、缺口、恢复

- 客户端 `StreamAssembler` 按 `eventId` 去重、按 `sequence` 找缺口。
- 收到 `gap`：断开，用 `after=lastSequence` 重连补放。
- 收到 `cursor_expired`：丢弃本地游标，`GET /api/assistant/sessions/{id}` 全量刷新。
- App 冷启动：先 `GET /health`（或直接打任意公开接口确认可达），再按上次 `lastSequence` 续流。

对应实现：`app/entry/src/main/ets/transport/sseParser.ts`（`Utf8ChunkDecoder` / `SseFrameParser` / `StreamAssembler`），测试在 `contract/test/transport.test.ts`。

## 5. 确认与幂等

- 确认令牌绑定 `taskId + action + taskVersion + 参数摘要 + 过期时间 + nonce`。
- 同一 `idempotencyKey` 第二次提交：返回 `replay=true`、同一 `taskId`，不产生第二条任务。
- 改过草稿再点旧卡：`409`，`violations` 里带 `K13`。

实测（demo profile）：

```powershell
cd backend
mvn -o spring-boot:run "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true" `
  "-Dspring-boot.run.jvmArguments=-Dai.ontology.location=file:./src/main/resources/ontology/campus-ontology.v1.json"

# 另开一个窗口
node scripts/smoke-assistant.mjs
```

输出应包含：

```
CONFIRM 200 outcome=OK taskId=1 card=progress
REPLAY 200 outcome=OK taskId=1 notes=["本次是重复确认，未产生新事实"]
STREAM 200 events=14 1:text.delta,...14:stream.end
```

## 6. 评测里的流断言

`evals/run.mjs` 的 case 加 `"stream": true` 后，会在这条 case 跑完后拉一次 `/api/stream?after=0`，
`check` 里可用：

| 判据 | 含义 |
|---|---|
| `streamKindsInclude` | 必须出现这些 kind |
| `streamMinEvents` | 事件条数下限 |
| `streamEndReason` | `stream.end.payload.reason` 必须等于它 |

已挂上的样本：`M1-006`、`M1-019`（`done`）、`M1-016`（`cancelled`）。2026-11-16 实测 40/40 通过。

## 7. 已知限制（如实写）

- `StreamEventLog` 是**进程内内存**：重启后流从头开始；M4 接正式事件表（`ai_event` 已落盘）。
- `tool.progress` 目前只有 `createTask` 一步，不是细粒度工具链。
- `GET /api/stream` 靠轮询 150 ms，不是服务端 push 推送；演示够用，压测需换 `SseEmitter` 事件驱动。
- 客户端 `Assistant.ets` 已编译通过，但**尚未在模拟器/真机跑过**（缺签名与模拟器实例）。
