# M2 · 九条最小自动化测试映射

> 课件 03 p71 要求一条都不能少。本表只指真文件、真用例，不写“已测试”这种空话。
> 状态：✅ 有自动化覆盖；◐ 有测试但只覆盖一半；❌ 缺。
> 后端命令统一在 `backend/` 下执行；契约命令在 `contract/` 下执行。

| # | 课件要求 | 测试文件 / 用例 | 命令 | 状态 |
|---|---|---|---|---|
| 1 | 非白名单组件被拒绝 | `backend/src/test/java/com/campusbridge/ai/SchemaValidatorTest.java` · `非白名单组件整张卡被拒`；`contract/test/contract.test.ts` · `恶意卡片会被白名单拦住` | `cd backend && mvn -o -q "-Dtest=SchemaValidatorTest" test`；`cd contract && npm run check` | ✅ |
| 2 | 绑定不存在字段时失败 | `backend/src/test/java/com/campusbridge/ai/SchemaValidatorTest.java` · `绑定不存在的字段被拒` | `cd backend && mvn -o -q "-Dtest=SchemaValidatorTest" test` | ✅ |
| 3 | 高风险 Action 缺少确认时失败 | `backend/src/test/java/com/campusbridge/ai/SchemaValidatorTest.java` · `高风险动作没有要求确认被拒` | `cd backend && mvn -o -q "-Dtest=SchemaValidatorTest" test` | ✅ |
| 4 | Task Model 更新后旧授权失效 | `backend/src/test/java/com/campusbridge/ai/AgentFlowTest.java` · `改过内容之后旧确认卡失效`（断言 `K13`，且不调用 `createTask`） | `cd backend && mvn -o -q "-Dtest=AgentFlowTest" test` | ✅ |
| 5 | 同一幂等键只产生一次业务事实 | `backend/src/test/java/com/campusbridge/ai/AgentFlowTest.java` · `确认后建单_重复确认不再建单`（`verify(taskCommands, times(1))`） | `cd backend && mvn -o -q "-Dtest=AgentFlowTest" test` | ✅ |
| 6 | 刷新后恢复 `lastStableVersion` | 服务端会话快照：`backend/src/test/java/com/campusbridge/unit/ViewSerializationTest.java`；客户端草稿模型：`app/entry/src/main/ets/domain/draftModel.ts` | `cd backend && mvn -o -q "-Dtest=ViewSerializationTest" test` | ◐ |
| 7 | 投影自动移除禁止动作 | `backend/src/test/java/com/campusbridge/ai/SchemaValidatorTest.java` · `非白名单组件整张卡被拒`；客户端 `app/entry/src/main/ets/ui/cardGuard.ts` | `cd backend && mvn -o -q "-Dtest=SchemaValidatorTest" test` | ◐ |
| 8 | UI 展示值与工具参数摘要一致 | `backend/src/test/java/com/campusbridge/ai/SchemaValidatorTest.java` · `卡片展示值与令牌摘要不一致被拒` | `cd backend && mvn -o -q "-Dtest=SchemaValidatorTest" test` | ✅ |
| 9 | 工具失败时不得写入成功事实 | `backend/src/test/java/com/campusbridge/ai/FaultSwitchTest.java` · `tool_fail返回DENIED且不调用业务端口`；`evals/cases/M1-015-工具失败.json` | `cd backend && mvn -o -q "-Dtest=FaultSwitchTest" test`；`cd evals && node run.mjs --live http://127.0.0.1:8081` | ✅ |

## ⑥ 和 ⑦ 为什么还是 ◐

- **⑥ 刷新后恢复 `lastStableVersion`**：服务端已经能通过 `GET /api/assistant/sessions/{sessionId}` 返回会话状态和草稿，`ViewSerializationTest` 锁住了返回字段；客户端 `draftModel.ts` 也有版本号，但还没有把「冷启动 → 拉会话 → 用 `lastStableVersion` hydrate」这一步接进 ArkUI 页面。**缺的是客户端恢复编排，不是服务端事实。** 下一步：在 `DraftStore` 加 `restore(snapshot)`，在 `Shell` 冷启动时调 `GET session`，再用 `contract/test/domain.test.ts` 锁回放。
- **⑦ 投影自动移除禁止动作**：当前是**整张卡拒绝**（服务端 `SchemaValidator` + 客户端 `cardGuard`），不是「删掉一个动作再重投影」。整卡拒绝更安全，但课件要的是投影阶段裁剪；两者语义不同，不能把拒绝写成裁剪。下一步：在 `SchemaValidator` 前增加 `project(card, viewer)`，只保留白名单动作并补测试。

## 一键回归

```powershell
cd backend
mvn -o -q "-Dtest=SchemaValidatorTest,AgentFlowTest,FaultSwitchTest,ViewSerializationTest" test

cd ..\contract
npm run check
```
