# M3 · 用 OpenCode Go + DeepSeek Flash 跑真实模型

> 2026-11-23 实测通过：`START mode=llm`，完整链路 `confirm outcome=OK taskId=2`。
> 端点：`https://opencode.ai/zen/go/v1`；模型：`deepseek-v4.1-flash`。
> 本机 `OPENCODE_API_KEY` 在 **User 环境变量**里；命令只引用变量，不打印 key。

## 1. 本次为此改了什么

| 改动 | 原因 |
|---|---|
| `OpenAiCompatibleClient` 加 `x-opencode-session` 请求头 | OpenCode Go 硬性要求；缺了直接 400 `MissingSessionID` |
| 请求里加“当前时间（Asia/Shanghai）” | 否则模型把“今晚”算成训练数据里的日期（实测算成 05-07） |
| Prompt 加默认值规则 | `onlyInAppContact` 默认 true；用户没提就别猜 false；`completionStandard` 至少 10 个汉字 |
| `ai.model.timeout-ms=60000` | DeepSeek Flash 带 reasoning，4 秒默认值会误判超时 |
| App `Assistant.ets` readTimeout 15s → 60s | 真模型首轮可能 10–30 秒，避免客户端先断 |
| `scripts/smoke-assistant.mjs` 改动态截止时间 + 干净目标 | 避免硬编码 2026-11-16 被 K04 拒绝；避免宿舍目标触发 K05 风险披露 |

## 2. 起后端（一个终端）

```powershell
# 从 User 环境变量取 key，只放进子进程，不打印
$env:AI_MODEL_API_KEY = [Environment]::GetEnvironmentVariable('OPENCODE_API_KEY','User')
if (-not $env:AI_MODEL_API_KEY) { throw 'OPENCODE_API_KEY 不在 User 环境变量里' }

cd D:\MWSE-2026\CampusBridge
mvn -o -f backend/pom.xml spring-boot:run `
  "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true" `
  "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --ai.model.mode=llm --ai.model.base-url=https://opencode.ai/zen/go/v1 --ai.model.name=deepseek-v4.1-flash --ai.model.session-header=x-opencode-session --ai.model.timeout-ms=60000 --ai.model.routing.enabled=false"
```

关键参数：

| 参数 | 值 | 说明 |
|---|---|---|
| `ai.model.mode` | `llm` | 强制走模型；失败会 `degraded=true` 退回规则 |
| `ai.model.base-url` | `https://opencode.ai/zen/go/v1` | OpenAI 兼容端点 |
| `ai.model.name` | `deepseek-v4.1-flash` | 用户要的 DeepSeek Flash |
| `ai.model.session-header` | `x-opencode-session` | OpenCode Go 必需；值自动取会话 ID |
| `ai.model.timeout-ms` | `60000` | 给 reasoning 留时间 |
| `ai.model.routing.enabled` | `false` | demo 配置里的路由模型名是 longcat，不走 |

## 3. 跑完整流程（另一个终端）

```powershell
cd D:\MWSE-2026\CampusBridge
node scripts/smoke-assistant.mjs
```

预期（2026-11-23 实测）：

```text
START 200 state=PROPOSING intent=代取快递 card=comparison mode=llm degraded=false
SELECT option=now
  -> 200 state=AWAITING_CONFIRMATION card=confirmation
CONFIRM CARD taskVersion=15 blocks=2 token=yes
CONFIRM 200 outcome=OK taskId=2 card=progress
REPLAY 200 outcome=OK taskId=2 notes=["本次是重复确认，未产生新事实"]
STREAM 200 events=10 1:text.delta,2:schema.card,...,10:stream.end
```

`mode=llm` 表示真的走了 DeepSeek Flash；如果看到 `mode=rule degraded=true`，看后端日志/`/api/events` 里的 `model.error`，通常是 key、网络或 60s 超时。

## 4. App 真机/模拟器

```powershell
cd D:\MWSE-2026\CampusBridge
powershell -ExecutionPolicy Bypass -File .\scripts\build-app.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\emulator-net.ps1
hdc install -r .\app\entry\build\default\outputs\default\entry-default-unsigned.hap
hdc shell aa start -a EntryAbility -b edu.nju.campusbridge2
```

App 里点「助手」→「进入助手对话」→「一句话开始」即可；`Assistant.ets` 已把读超时改成 60s。

## 5. 注意

- **别把 key 写进仓库**；`application.yml` 已被 `.gitignore` 忽略，本文只引用环境变量。
- 宿舍/上门/深夜目标会触发 **K05 风险披露**，这是特性；演示完整链路用“北门菜鸟驿站”这种干净目标。
- 若想恢复纯规则演示：去掉 `--ai.model.mode=llm`（demo 默认 `rule`），不需要 key。
- 40 条评测逐条打真实模型会很慢且有随机性；快速验收用 `smoke-assistant.mjs`，全量评测仍建议规则基线跑。
