# 本地运行流程（从零到 App 看到结果）

> 适用：Windows、DevEco Studio（API 22 模拟器）、Node 22、Maven、Java 17+。
> 工作目录统一为 `D:\MWSE-2026\CampusBridge`。
> 真实模型：OpenCode Go / `deepseek-v4.1-flash`；规则基线不需要 key。

---

## 0. 一张图看懂

```text
模拟器 App
   │  127.0.0.1:8081（设备里的 localhost）
   ▼
hdc rport 8081  ──►  电脑 127.0.0.1:8081
                        │
                        ▼
              Spring Boot demo（H2 内存库）
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
       规则基线（rule）      ModelGateway → OpenCode Go
                                   deepseek-v4.1-flash
```

关键点：**设备里的 `127.0.0.1` 是设备自己**，必须做 `hdc rport` 才能访问电脑上的后端。

---

## 1. 启动模拟器

1. 打开 DevEco Studio → **Tools → Device Manager**。
2. 启动 **Phone / API 22** 模拟器。
3. 确认设备在线：

```powershell
$hdc = 'D:\DevEco\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
& $hdc list targets
# 期望：127.0.0.1:5555
```

> 模拟器每次重启，第 3 步的端口转发都要重做。

---

## 2. 起后端（终端 A）

### 2.1 真实模型（OpenCode Go / DeepSeek Flash）

```powershell
$env:AI_MODEL_API_KEY = [Environment]::GetEnvironmentVariable('OPENCODE_API_KEY','User')
if (-not $env:AI_MODEL_API_KEY) { throw 'OPENCODE_API_KEY 不在 User 环境变量里' }

cd D:\MWSE-2026\CampusBridge
mvn -o -f backend/pom.xml spring-boot:run `
  "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true" `
  "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --ai.model.mode=llm --ai.model.base-url=https://opencode.ai/zen/go/v1 --ai.model.name=deepseek-v4.1-flash --ai.model.session-header=x-opencode-session --ai.model.timeout-ms=60000 --ai.model.routing.enabled=false"
```

看到 `Tomcat started on port 8081` 即成功。

验证：

```powershell
Invoke-WebRequest 'http://127.0.0.1:8081/api/v1/tasks?page=1&size=1' -UseBasicParsing |
  Select-Object StatusCode
# 期望：200
```

### 2.2 规则基线（没有 key / 只想稳定演示）

```powershell
cd D:\MWSE-2026\CampusBridge
mvn -o -f backend/pom.xml spring-boot:run `
  "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true"
```

demo 默认 `ai.model.mode=rule`，不需要 key，也不会调用公网。

---

## 3. 端口反代（模拟器启动后做一次）

```powershell
cd D:\MWSE-2026\CampusBridge
powershell -ExecutionPolicy Bypass -File .\scripts\emulator-net.ps1
```

验证：

```powershell
& $hdc fport ls
# 期望两行：
# tcp:4390 tcp:4390 [Reverse]
# tcp:8081 tcp:8081 [Reverse]
```

如果 `[Empty]`，说明模拟器重启过，重跑本步。

---

## 4. CLI 快速验收（终端 B，不用开 App）

```powershell
cd D:\MWSE-2026\CampusBridge
node scripts/smoke-assistant.mjs
```

期望（真实模型）：

```text
START 200 state=PROPOSING card=comparison mode=llm degraded=false
SELECT option=now
  -> 200 state=AWAITING_CONFIRMATION card=confirmation
CONFIRM CARD taskVersion=... token=yes
CONFIRM 200 outcome=OK taskId=N card=progress
REPLAY 200 outcome=OK taskId=N notes=["本次是重复确认，未产生新事实"]
STREAM 200 events=10 ... 10:stream.end
```

看到 `mode=llm` 才是真的走了 DeepSeek Flash；`mode=rule` 表示降级/规则基线。

---

## 5. 构建、安装、启动 App

```powershell
cd D:\MWSE-2026\CampusBridge
powershell -ExecutionPolicy Bypass -File .\scripts\build-app.ps1

$hdc = 'D:\DevEco\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
$hap = 'D:\MWSE-2026\CampusBridge\app\entry\build\default\outputs\default\entry-default-unsigned.hap'
& $hdc install -r $hap
& $hdc shell aa start -a EntryAbility -b edu.nju.campusbridge2
```

模拟器里：

1. 首页显示 **「后端在线」**；
2. 点「进入助手对话」；
3. 输入干净目标，例如：`今晚九点去北门菜鸟驿站帮我取三个快递，预算8元`；
4. 点「一句话开始」，等 **10–30 秒**（模型在 reasoning，不是卡死）；
5. 依次看澄清/比较/确认卡，点「确认发布」；
6. 回「任务」页点「刷新」，能看到刚建的任务。

> 宿舍/上门/深夜目标会触发 **K05 风险披露**，确认被 409 是预期行为；首跑用上面的干净目标。

---

## 6. 演示建议（5 分钟）

| 时间 | 做什么 | 看什么 |
|---|---|---|
| 0:00 | 打开 App「助手」 | 底部导航、后端在线 |
| 0:30 | 输入干净目标 → 开始 | 模型理解 + 比较卡 |
| 1:30 | 选方案 → 确认 | 确认卡带令牌 |
| 2:30 | 回任务页刷新 | 新任务出现在 1.0 列表 |
| 3:00 | 停后端 → 回 App | L2 降级横条、助手禁用；任务页可重试 |
| 4:00 | 起后端 → 重试 | 恢复在线 |

---

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| App 显示「后端不可用」 | `hdc fport ls` 为空（模拟器重启过） | 重跑第 3 步；再重启 App |
| 仍不可用 | 后端没起/端口被占 | `Get-NetTCPConnection -LocalPort 8081`，重启后端 |
| `400 MissingSessionID` | 请求没带 `x-opencode-session` | 本仓库已支持；确认启动参数里有 `--ai.model.session-header=x-opencode-session` |
| `mode=rule degraded=true` | key 无效/网络/60s 超时 | 看后端日志 `model.error`；确认 User 环境变量和端点 |
| 确认 409 `K04` | 模型把截止时间算成过去 | 已注入当前时间；重启后端并重跑 |
| 确认 409 `K05` | 宿舍/上门/深夜需要风险披露 | 换干净目标，或先做风险确认 |
| 确认 409 `K10` | 完成标准太短 | 换更具体的目标，或补完成标准 |
| 首次点开始很慢 | DeepSeek Flash reasoning | 等 10–30 秒；客户端已设 60s |
| 改代码后 App 没变 | 没重新 build/install | 重跑第 5 步 |

---

## 8. 停止

- 后端终端：`Ctrl+C`。
- App：模拟器里划掉，或 `hdc shell aa force-stop edu.nju.campusbridge2`。
- 模拟器：Device Manager 里关闭。

---

## 9. 最短命令清单

```powershell
# 终端 A
$env:AI_MODEL_API_KEY = [Environment]::GetEnvironmentVariable('OPENCODE_API_KEY','User')
cd D:\MWSE-2026\CampusBridge
mvn -o -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true" "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --ai.model.mode=llm --ai.model.base-url=https://opencode.ai/zen/go/v1 --ai.model.name=deepseek-v4.1-flash --ai.model.session-header=x-opencode-session --ai.model.timeout-ms=60000 --ai.model.routing.enabled=false"

# 终端 B（模拟器启动后）
cd D:\MWSE-2026\CampusBridge
powershell -ExecutionPolicy Bypass -File .\scripts\emulator-net.ps1
node scripts/smoke-assistant.mjs
```
