# 流式协议 v1（冻结于 2026-11-16）

> 实现：客户端 `transport/`（张琼文）+ 服务端 `/api/stream`（谢易轩）。
> 本文与 `src/transport/events.ts` 是同一份契约的文字版；两者不一致时以 `events.ts` 为准并当天修正本文。
> 老师第 6 课的 demo 包是空壳（259 字节），所以这套协议是我们自己定的，不是抄的。

## 1. 两条通道，别混

| 通道 | 用途 | 特点 |
|---|---|---|
| `POST /api/assistant/...` | 所有**副作用与动作**：开始、回答、选择、确认、取消 | 普通 HTTPS，带幂等键；成功才产生业务事实 |
| `GET /api/stream?sessionId=&after=` | **只推送**：文本片段、卡片、工具进度、任务快照、结束 | `text/event-stream`；断开可重连，不产生业务事实 |

**红线：客户端不能靠"读到某段文本"决定去建单。** 建单只认 `POST .../confirm` 的返回和 `task.snapshot`。

## 2. 事件信封

```json
{
  "protocol": "campushub.stream.v1",
  "eventId": "evt-1720-000012",
  "sequence": 12,
  "sessionId": "s-9f3",
  "ts": "2026-11-16T08:12:03.512Z",
  "kind": "schema.card",
  "payload": { "card": { "...": "UiCard" } }
}
```

| 字段 | 类型 | 规则 |
|---|---|---|
| `protocol` | string | 必须等于 `campushub.stream.v1`，否则客户端拒绝并降级到固定页面 |
| `eventId` | string | 会话内唯一；**去重靠它**，重连重发时不变 |
| `sequence` | number | 会话内单调 +1；**缺口靠它**，跳号说明中间丢了 |
| `sessionId` | string | 会话 |
| `ts` | string | ISO 8601 UTC，只做展示与排序参考，不参与判定 |
| `kind` | string | 五种之一，见下 |
| `payload` | object | 按 kind 取值，未定义字段一律忽略 |

## 3. 五种 kind

| kind | payload | 客户端要做什么 |
|---|---|---|
| `text.delta` | `{ text: string }` | 追加到当前助理气泡；**不触发任何业务** |
| `schema.card` | `{ card: UiCard }` | 先过白名单校验，通过才渲染；拒绝时记一条拒绝事件并保持上一张卡 |
| `tool.progress` | `{ toolName, status: started\|progress\|done\|failed, progress?, message? }` | 显示进度条；`failed` 要显示可读原因 |
| `task.snapshot` | `{ taskId, taskStatus, taskVersion }` | **以服务端快照为准**更新任务状态与版本；旧版本卡立即置灰 |
| `stream.end` | `{ reason: done\|cancelled\|error\|cursor_expired\|model_unavailable, message? }` | `done` 正常收尾；`cursor_expired` 全量刷新；`model_unavailable` 进降级 |

## 4. SSE 帧

```
id: 12
event: message
data: {"protocol":"campushub.stream.v1","sequence":12,...}

```

- 一个帧以**空行**结束；`data:` 行可能被网络拆开，也可能分多行。
- `id:` 只是 SSE 层的重连提示；**协议判定一律用 JSON 里的 `eventId` / `sequence`**。
- 客户端解析顺序：字节缓冲 → 保留未完成的 UTF-8 字符 → 按空行切帧 → 取 `data:` 拼 JSON → 校验信封 → 接纳。

## 5. 去重、缺口、恢复

| 情况 | 判定 | 动作 |
|---|---|---|
| 收到已见过的 `eventId` | 重发 | 丢弃，不重复渲染 |
| `sequence > lastSequence + 1` | 中间丢了 | 断流，用 `after=lastSequence` 重连补放 |
| 重连成功 | 服务端从 `after+1` 开始发 | 继续接纳，不重置已渲染的 UI |
| 收到 `cursor_expired` | 服务端已清掉历史 | 丢弃本地游标，全量刷新（`GET /api/assistant/sessions/{id}`） |
| App 重启 | 本地有 `lastEventId` / `lastSequence` | 先 `GET /health`，再 `GET /api/stream?after=lastSequence` |

`after=N` 的语义：**只发 sequence > N 的事件**。`after` 缺省表示从头（仅演示用）。

## 6. 卡片（UiCard）与白名单

卡片结构见 `src/protocol/types.ts` 的 `UiCard`；白名单见 `src/protocol/whitelist.ts`。

三条硬规则（校验器实现，不是渲染器兜底）：

1. `blocks[].type` 不在 `ALLOWED_BLOCKS` → **整张卡拒绝**，不是丢掉那一个块。
2. `blocks[].field` 不在 `DRAFT_FIELDS` → 拒绝。
3. `actions[].actionId` 属于副作用动作（`confirm`）却没有 `requiresConfirmation: true` → 拒绝。

版本：`schemaVersion !== "1.0"` → 拒绝并降级到固定页面。**不猜、不兼容。**

## 7. 错误与幂等

| 场景 | HTTP | 返回 |
|---|---|---|
| 令牌过期 / 被篡改 / nonce 复用 / `taskVersion` 落后 | 409 | `{ ok:false, violations:[{ ruleId:"K13", ... }] }` |
| 同一 `idempotencyKey` 第二次提交 | 200 | `{ ok:true, replay:true, taskId }`，**不产生第二条业务事实** |
| 工具失败 | 200 或 500 | `{ ok:false, message }`，**不得**出现 `task.snapshot` 成功状态 |

## 8. 降级

| 级别 | 表现 | 谁决定 |
|---|---|---|
| L0 | 正常卡片，`card.generatedBy = "model"` | 模型 |
| L1 | 规则整理的卡片，`card.generatedBy = "rule"`，`warnings` 里有说明 | 服务端 |
| L2 | 流打不开或协议版本不认识，客户端直接进传统页面（1.0 的 Vue 页面逻辑复刻） | 客户端 |

**L1/L2 必须能从界面上看出来**，不许把规则结果伪装成模型成功。
