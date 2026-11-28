# M4 证据包

> 2026-11-28 整理。环境：Windows + Java 23（Maven `--release 17`）+ demo H2 + 规则基线；
> 后端 `http://127.0.0.1:8081`，契约 `contract/`。

## 1. 固定评测集

| 项 | 结果 | 位置 |
|---|---|---|
| 40 条端到端评测 | **40/40**，八类分布 5/4/5/6/5/5/5/5 | `evals/reports/run-2026-11-23T06-28-35-671Z.json` |
| 指标表 | P95 51ms、平均 1.45 步、失败 0 条 | `docs/M4-evals.md` |
| 复跑命令 | `cd evals; node run.mjs --live http://127.0.0.1:8081` | `evals/run.mjs` |

## 2. 个人记忆三类 + Agent 接入

- 证据：`02-agent-memory.txt`。现场挑战原题走完澄清 → 比较 → 确认建单；比较卡多出“只找上次那位”，确认后写入 `DECLARED` 记忆，新会话能召回。
- 记忆“修改”端点已补：`PUT /api/memory/{id}`（`MemoryController`）。
- 测试：`AgentFlowTest` 6 项、`MemoryStoreTest` 6 项、`MemoryControllerIntegrationTest` 4 项全绿。
- 文档：`docs/M4-data.md`（端点、三类记忆、Agent 接入说明）。

## 3. 红队五类

| 项 | 结果 | 位置 |
|---|---|---|
| 五类用例 | **5/5**（2026-11-22，H2 demo） | `docs/M4-redteam.md` |
| 复跑 | `node evals/redteam/run.mjs --base http://127.0.0.1:8081` | `evals/redteam/` |

## 4. 数据治理

- `deploy/mysql/governance.sql`：只读聚合审计，输出七项规则异常计数。
- H2 MySQL 模式 + 独立 MySQL 9.4.0 均验收，七项异常计数为 0（见 `docs/M4-data.md`）。
- 数据目录 / 质量规则 / 权限矩阵 / 血缘表都在 `docs/M4-data.md`。

## 5. 交互事件

- 16 个业务事件 + `model.*` + 5 种流事件；每条 payload 带
  `modelVersion / ontologyVersion / promptVersion / schemaVersion / toolVersion`。
- 映射表见 `docs/M4-data.md` 的“交互全过程事件（16 类）”。

## 6. 边界与未做

- 飞轮 5 人实测未做：选修课口径，保留设计，不填假数字（`docs/M4-evals.md` 第三节）。
- 任务完成由 1.0 完成接口维护，助手不重复记 `task.completed`；人工接管本版本未做。
