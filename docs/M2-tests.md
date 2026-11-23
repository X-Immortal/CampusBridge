# M2 · 九条最小自动化测试映射

> 本表把每条要求点到**一个真实存在的测试文件/用例**，并给出可直接复制的验收命令。
> 状态：✅ 已覆盖 / ◐ 部分覆盖（附原因）/ ❌ 未覆盖。

## 一、映射总表

| # | 课件要求 | 测试文件 / 用例 | 验收命令 | 状态 |
|---|---|---|---|---|
| 1 | 非白名单组件被拒绝 | `backend/.../ai/SchemaValidatorTest.java` → `非白名单组件整张卡被拒`；`contract/test/contract.test.ts` → `白名单会拒绝外来词`、`恶意卡片会被白名单拦住` | `cd backend; mvn -o -q "-Dtest=SchemaValidatorTest" test`；`cd contract; npm run check` | ✅ |
| 2 | 绑定不存在字段时失败 | `SchemaValidatorTest.java` → `绑定不存在的字段被拒`；`contract.test.ts` → `isKnownDraftField('dormitoryNumber') === false` | `cd backend; mvn -o -q "-Dtest=SchemaValidatorTest" test` | ✅ |
| 3 | 高风险 Action 缺少确认时失败 | `SchemaValidatorTest.java` → `高风险动作没有要求确认被拒`、`确认动作没有令牌被拒` | `cd backend; mvn -o -q "-Dtest=SchemaValidatorTest" test` | ✅ |
| 4 | Task Model 更新后旧授权失效 | `AgentFlowTest.java` → `改过内容之后旧确认卡失效`（K13）；`PolicyEngineTest.java` → `K13_令牌版本落后于当前版本被拒`；`TokenServiceTest.java` → `用过一次就失效`、`过期之后失效`；`contract/test/domain.test.ts` → `出卡后改字段：旧卡立即失效，确认被拒` | `cd backend; mvn -o -q "-Dtest=AgentFlowTest,PolicyEngineTest,TokenServiceTest" test`；`cd contract; npm run check` | ✅ |
| 5 | 同一幂等键只产生一次业务事实 | `AgentFlowTest.java` → `确认后建单_重复确认不再建单`（`verify(createTask, times(1))`）；`scripts/smoke-assistant.mjs` → `REPLAY` 段返回同一 `taskId` | `cd backend; mvn -o -q "-Dtest=AgentFlowTest" test` | ✅ |
| 6 | 刷新后恢复 `lastStableVersion` | `contract/test/domain.test.ts` → `比本地旧的 task.snapshot 不覆盖新状态`（版本守卫） | `cd contract; npm run check` | ◐ |
| 7 | 投影自动移除禁止动作 | `SchemaValidatorTest.java` → `非白名单组件整张卡被拒`（整卡拒绝策略，非裁剪） | `cd backend; mvn -o -q "-Dtest=SchemaValidatorTest" test` | ◐ |
| 8 | UI 展示值与工具参数摘要一致 | `SchemaValidatorTest.java` → `卡片展示值与令牌摘要不一致被拒`（`paramDigest` 比对）；`TokenServiceTest.java` → `签发后可以校验通过`（`paramDigest` 绑定） | `cd backend; mvn -o -q "-Dtest=SchemaValidatorTest,TokenServiceTest" test` | ✅ |
| 9 | 工具失败时不得写入成功事实 | `evals/cases/M1-015-工具失败.json`（`finalOutcome=DENIED`、`taskIdNull`）；`PolicyEngineTest.java` → `K06_没有确认令牌不能建单` | `cd evals; node run.mjs --live http://127.0.0.1:8081` | ✅ |

## 二、◐ 两项的说明

### ⑥ 刷新后恢复 `lastStableVersion`（◐）
- **已覆盖**：`domain.test.ts` 已验证「旧版本 `task.snapshot` 不覆盖新状态」，即版本单调守卫在，能防止回放/乱序把状态拉回旧版。
- **未覆盖**：真正的**冷启动恢复**——页面刷新 / App 重启后，从服务端拉回 `lastStableVersion` 重建草稿——尚未落地，当前 draft 只存内存。

### ⑦ 投影自动移除禁止动作（◐）
- **已覆盖**：服务端/契约对非法内容采用**整卡拒绝**（`SchemaValidatorTest.非白名单组件整张卡被拒`），禁止动作不会被渲染，安全边界成立。
- **未覆盖**：课件描述的「**移除禁止动作后重新投影**剩余合法内容」的裁剪（trim-and-reproject）策略未实现——现在是拒绝而非降级裁剪。

## 三、全量回归

单条命令跑不全时，用整包验证，确保映射的测试都还绿：

```powershell
cd CampusBridge\backend;  mvn -o -q "-Dtest=SchemaValidatorTest,PolicyEngineTest,TokenServiceTest,AgentFlowTest" test
cd CampusBridge\contract; npm run check
cd CampusBridge\evals;    node run.mjs --live http://127.0.0.1:8081
```
