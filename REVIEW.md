# 审阅说明 · 2026-11-23 完整版

> 分支：`review/complete-2026-11-23`（基于 `main@9a3de73` + 陆俊达 PR #1 `7686027`）
> 状态：**本地分支，未 push、未合 main**。请先审这个分支，确认后再合。
> 目标：把 11/23 合并验收缺的 M3 / 压测故障 / App 页面 / 文档与证据补齐，形成一版可跑、可复现的完整版本。

---

## 1. 这一版包含什么

| 模块 | 内容 | 关键文件 |
|---|---|---|
| M4（陆俊达 PR） | 个人记忆三类、红队 5 类用例/runner/夹具、治理 SQL、M4 文档 | `backend/.../memory/**`、`evals/redteam/**`、`deploy/mysql/governance.sql`、`docs/M4-*.md` |
| M3 模型网关 | 路由、日配额、理解缓存、成本估算；`model.route/cache/quota/cost` 事件 | `backend/.../gateway/model/{ModelRouter,ModelQuota,ModelCache,ModelGateway}.java` |
| M3 故障开关 | `ai.fault.mode = none / model_timeout / tool_fail`；`tool_fail` 不落库 | `backend/.../gateway/model/FaultSwitch.java`、`tool/ToolGateway.java` |
| 事件版本血缘 | 每条事件补 `modelVersion/ontologyVersion/promptVersion/schemaVersion/toolVersion` | `backend/.../events/{VersionInfo,EventRecorder}.java` |
| M3 测试 | 路由/缓存/配额/成本、三个故障、版本字段 | `backend/src/test/java/com/campusbridge/ai/{ModelGatewayPlanTest,FaultSwitchTest,EventRecorderVersionTest}.java` |
| 压测/故障脚本 | 三类压测（50 并发全链路、20 并发 SSE、同幂等键并发确认）；三个故障（超时/重复工具/流中断重连） | `evals/load/run-load.mjs`、`evals/faults/run-faults.mjs`、`evals/lib/api.mjs` |
| 九条测试映射 | 9 行表，逐行指到真实测试文件/用例/命令；⑥⑦ 写清为什么还是 ◐ | `docs/M2-tests.md` |
| App Shell | 底部导航（助手/任务）、后端探针、L2 降级横条、平板 master-detail（≥840vp） | `app/.../pages/Shell.ets` |
| App 任务页 | 任务列表/详情接 1.0 公开接口；手机列表→详情页；宽屏左列表右详情 | `app/.../pages/{TaskList,TaskDetail}.ets`、`app/.../components/{TaskListPanel,TaskDetailPanel}.ets` |
| App 白名单 | 坏卡整张拒绝、保留上一张；Index/Assistant 共用 | `app/.../ui/cardGuard.ts`、`pages/Assistant.ets` |
| 文档/证据 | M3 ADR（四问 + 路由/配额/缓存/成本）、SLO/压测/故障实测、证据包 | `docs/M3-adr-llm.md`、`docs/M3-slo-loadtest.md`、`evidence/M3/**` |
| demo 修复 | 补 `user_profiles`（任务列表 500）；补 H2 test classpath 复现命令 | `backend/.../db/demo-{schema,data}.sql`、`docs/M4-redteam.md` |

---

## 2. 我实际跑过的验证

| 验证 | 结果 | 命令/证据 |
|---|---|---|
| 后端全量测试 | **300 项，0 失败，0 错误，40 跳过，BUILD SUCCESS** | `cd backend && mvn -o test` |
| M3 定向测试 | **10/10**（网关 5 + 故障 3 + 版本 2） | `mvn -o -q "-Dtest=ModelGatewayPlanTest,FaultSwitchTest,EventRecorderVersionTest" test` |
| 契约/transport/domain | **23/23** | `cd contract && npm run check` |
| 40 条评测（live） | **40/40** | `cd evals && node run.mjs --live http://127.0.0.1:8081` |
| 压测 | assistant 50/50（P95 500.5ms）；stream 20/20（首帧 P95 64.1ms）；idempotency 10/10（唯一 taskId=1，重复建单 0） | `node evals/load/run-load.mjs --base http://127.0.0.1:8081`；`evals/reports/load-2026-11-23T06-27-16-187Z.json` |
| 三个故障 | **3/3 PASS** | `node evals/faults/run-faults.mjs --base http://127.0.0.1:8081` |
| HAP 编译 | **BUILD SUCCESSFUL，299 KB** | `powershell -ExecutionPolicy Bypass -File scripts/build-app.ps1` |
| 模拟器安装/前台 | `install bundle successfully`；`EntryAbility` `state #FOREGROUND` | `evidence/M3/05-aa-dump.txt` |
| App 真实 UI | Shell 在线、任务页空态→1 条→详情页字段全出 | `evidence/M3/01–04-*.json`（布局树） |

> 模拟器 `snapshot_display` / `uitest screenCap` 本轮返回 `Failed to get display pixelMap`，
> 截图拿不到，所以用 `uitest dumpLayout` 保存真实布局树；这不是拿旧截图充数。

---

## 3. 审阅入口（按这个顺序看）

1. `git diff main...review/complete-2026-11-23 --stat` 看范围。
2. `docs/M3-adr-llm.md` 看 M3 的决策/四问/路由/成本/降级是否说得通。
3. `docs/M3-slo-loadtest.md` 看数字和复现条件。
4. `docs/M2-tests.md` 看九条测试映射，尤其 ⑥⑦ 的 ◐ 理由是否接受。
5. `app/.../pages/Shell.ets` 看底部导航/L2/平板断点；`Assistant.ets` 看坏卡拒绝。
6. `backend/.../gateway/model/ModelGateway.java` 看缓存/配额/成本/故障的顺序和事件。
7. `evidence/M3/README.md` 看证据包索引。

---

## 4. 已知缺口（不藏）

| 缺口 | 影响 | 下一步 |
|---|---|---|
| 平板 master-detail 只在代码里，没有平板模拟器实拍 | 演示时可能布局不如预期 | 用 DevEco 平板 API 22 起一次，确认 ≥840vp 左列表右详情，补截图 |
| 九条测试的 ⑥ `lastStableVersion`、⑦ 投影裁剪仍是 ◐ | M2 这两条只能拿部分分 | ⑥ 加 `DraftStore.restore(snapshot)` + 冷启动拉 session；⑦ 加 `project(card, viewer)` 只删动作再校验 |
| 未配置真实模型 key | M3 的模型延迟/成本是估算值，不是供应商实测 | 配 `ai.model.base-url/api-key` 后跑同一压测，回填 `M3-slo-loadtest.md` |
| M4 飞轮五人实验未做（可选） | M4 少一块加分 | 按 `docs/M4-evals.md` 的设计约 5 人，补两张表 |
| App 截图接口不可用 | 证据是布局树不是图片 | 换一台能截图的模拟器/真机补 `evidence/M3/screenshots/` |
| 陆俊达仍无仓库写权限 | 后续只能 fork+PR | 让谢（admin）把 `ljd-sudo` 加进 collaborators |

---

## 5. 合并建议

- 先审 `review/complete-2026-11-23`；确认后 `git switch main && git merge --ff-only review/complete-2026-11-23`（或走 PR）。
- 合完先跑 `cd backend && mvn -o test` 和 `cd contract && npm run check`，再跑一次 `node evals/load/run-load.mjs`。
- PR #1 的文档命令已在本分支修正（`-Dspring-boot.run.useTestClasspath=true`），合 PR 时不会再踩 H2 驱动坑。

## 6. 分支上的提交

以 `git log --oneline main..HEAD` 为准。本分支至少包含：

- `7686027` 陆俊达 PR #1（M4 记忆/红队/治理）
- 本轮实现提交（M3 网关/故障/测试、App Shell/任务页、压测/故障脚本、文档与证据）

> 不预填假 hash；审阅时用上面的命令看实际提交。
