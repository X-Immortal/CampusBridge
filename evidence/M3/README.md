# M3 证据包

> 验收日：2026-11-23。环境：Windows + Java 23（Maven `--release 17`）+ demo H2 + 模拟器
> `127.0.0.1:5555`（Phone / API 22）。后端 `http://127.0.0.1:8081`，App 通过
> `scripts/emulator-net.ps1` 的 `hdc rport 8081` 访问。

## 1. 构建与安装

| 项 | 结果 | 文件/命令 |
|---|---|---|
| 后端全量测试 | **300 项，0 失败，0 错误，40 跳过，BUILD SUCCESS** | `cd backend && mvn -o test` |
| M3 定向测试 | `ModelGatewayPlanTest` 5 + `FaultSwitchTest` 3 + `EventRecorderVersionTest` 2 全绿 | `mvn -o -q "-Dtest=ModelGatewayPlanTest,FaultSwitchTest,EventRecorderVersionTest" test` |
| HAP 编译 | **BUILD SUCCESSFUL，entry-default-unsigned.hap 299 KB** | `powershell -File scripts/build-app.ps1` |
| 模拟器安装 | `install bundle successfully`，`EntryAbility` `state #FOREGROUND` | `05-aa-dump.txt` |

## 2. App 实测（布局树，模拟器截图接口本轮不可用）

模拟器 `snapshot_display` / `uitest screenCap` 返回 `Failed to get display pixelMap`
（屏幕像素图拿不到），因此用 `uitest dumpLayout` 保存**真实布局树**作为证据；
每条 `01–04` 都能搜到对应文本。

| 文件 | 验证点 | 关键文本 |
|---|---|---|
| `00-l2-degraded.json` | 后端停掉/探针失败时的 L2 降级 | `L2 降级`、`后端离线`、`重试` |
| `01-shell-online.json` | Shell 底部导航 + 后端在线 + L2 未触发 | `助手`、`任务`、`后端在线` |
| `02-task-tab-empty.json` | 任务页空态 | `任务广场`、`共 0 条`、`暂时没有` |
| `03-task-list.json` | 助手建单后任务出现 | `共 1 条`、`帮取3个快递`、`待接单`、`¥8` |
| `04-task-detail.json` | 任务详情页字段 | `任务详情`、`报酬`、`截止时间`、`完成标准`、`演示同学` |
| `05-aa-dump.txt` | App 前台运行 | `state #FOREGROUND` |

**L2 降级**：把后端停掉/探针失败时，Shell 顶部出现
`L2 降级：后端不可用，助手暂停；任务页可重试`，助手按钮 `enabled=false`
（见 `00-l2-degraded.json`）；重启后端并点「重试」后恢复 `后端在线`（见 `01-shell-online.json`）。

## 3. 压测与故障

| 项 | 结果 | 文件 |
|---|---|---|
| 50 并发完整链路 | 50/50，0 错误，97.12/s，P95 500.5ms | `evals/reports/load-2026-11-23T06-27-16-187Z.json` |
| 20 并发 SSE | 20/20，首帧 P95 64.1ms，结束 P95 74.8ms | 同上 |
| 10 并发同幂等键确认 | 10/10，唯一 taskId=1，重复建单 0 | 同上 |
| 三个故障 | **3/3 PASS**（模型超时降级、重复工具调用、流中断重连） | `node evals/faults/run-faults.mjs --base http://127.0.0.1:8081` |
| M4 红队（回归） | **5/5 PASS** | `node evals/redteam/run.mjs --base http://127.0.0.1:8081`（需按 `docs/M4-redteam.md` 加载夹具） |

## 4. 本轮修掉的问题

见 `06-fixed-issues.md`：任务详情页在 `deliveryLocation=null` 时崩溃（已用 null-safe
`textOf` + `fieldRow` 修复并重新装机验证）；demo schema 缺 `user_profiles` 导致任务列表 500
（已补 demo schema/data，任务页恢复）。

## 5. 复现命令

```powershell
# 后端（H2 需要 test classpath 才有 h2 driver）
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.useTestClasspath=true"

# 全量测试
cd backend; mvn -o test

# 压测 / 故障
node evals/load/run-load.mjs --base http://127.0.0.1:8081
node evals/faults/run-faults.mjs --base http://127.0.0.1:8081

# App
powershell -ExecutionPolicy Bypass -File scripts/build-app.ps1
powershell -ExecutionPolicy Bypass -File scripts/emulator-net.ps1
hdc install -r app/entry/build/default/outputs/default/entry-default-unsigned.hap
hdc shell aa start -a EntryAbility -b edu.nju.campusbridge2
```
