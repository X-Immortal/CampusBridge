# M1 证据包

> 评审看的不是"我们做完了"，而是**这些结论各有一份能复查的东西**。
> 本目录每一项都对应 M1 的一条门槛。

---

## 一、交付物与证据对照

| M1 门槛 | 证据文件 | 证明了什么 |
|---|---|---|
| 可运行 Agent Harness | `screenshots/02` → `screenshots/05` | 一句话 → 追问 → 比较 → 确认 → 落库，四类卡片都在真服务上跑过 |
| 确认后才写库 | `screenshots/04`、`screenshots/05` | 确认前 `task=—`；确认后出现任务编号，事件链 `confirmation.issued → tool.request → tool.success → task.created` |
| 结构校验 / 白名单 | `eval-report.json`（M1-012/013/014） | Script 组件、未知字段、缺确认的按钮，三类恶意卡片全部被拒 |
| 追问 Policy | `screenshots/02` | 只问缺的三项，每项都带"为什么问"；没有编时间和地点 |
| 降级 | `eval-report.json`（M1-004/017） | 强制走 llm 而模型不可用 → `degraded=true`，卡片照出 |
| 工具失败不写假成功 | `eval-report.json`（M1-015） | 未认证用户建单 → `DENIED`、`taskId=null` |
| 本体能改变行为 | `logs/…`、`docs/M1-demo.md` §5 | 把 K09 的 `min` 从 5 改成 20 → reload → 同一张卡确认被 409 拒绝 |
| 旧确认卡失效 | `logs/k13-overturned.log` | K13 放宽成 WARN 时测试直接红：`expected: "DENIED" but was: "OK"` —— 证明这条约束是承重的 |
| 自动化测试 | `logs/mvn-test.log` | `Tests run: 190, Failures: 0, Errors: 0, Skipped: 0` |
| 评测集 | `eval-report.json` | 18 条，八类全覆盖，`--live` 18/18 |

---

## 二、截图（`screenshots/`）

| 文件 | 画面 |
|---|---|
| `01-初始界面.png` | 原型首屏，右上角是本体指纹与 20 / 23 / 15 的计数 |
| `02-澄清卡-缺信息先追问.png` | 输入"帮我取个快递"，只问三项；右侧是理解结果、假设和事件 |
| `03-比较卡-三种匹配方式.png` | 信息齐了出三种方案；摘要每行标「用户说的 / 系统推测」 |
| `04-确认卡-带令牌.png` | 带令牌的确认卡：确认前什么都没有 |
| `05-进度卡-已落库.png` | 任务 #2 落库；事件链完整可查 |

> 截图是在 `demo` profile（H2 内存库 + 规则基线）下拍的，所以任务编号从 #1 起。
> 复现命令见 [../../docs/M1-demo.md](../../docs/M1-demo.md)。

## 三、日志（`logs/`）

| 文件 | 内容 |
|---|---|
| `mvn-test.log` | 全量测试输出：190 项，0 失败（1.0 的 143 + M1 新增 47） |
| `k13-baseline.log` | 实验基线：K13 = BLOCK，AgentFlowTest 4/4 绿 |
| `k13-overturned.log` | 把 K13 改成 WARN 后：`expected: "DENIED" but was: "OK"` —— AI 的方案被推翻 |
| `k13-restored.log` | 恢复 BLOCK，重新跑回 4/4 绿 |
| `k13-first-attempt.log` | 第一次实验的失败尝试：测试同时触发了 K01，掩盖了 K13 —— 顺带修了测试设计 |

## 四、评测报告（`eval-report.json`）

`node evals/run.mjs --live http://127.0.0.1:8081` 的原始输出，含每条样本的状态、卡片类型、任务号与失败原因。
现场可以重新跑一遍，和这份对照。

---

## 五、还没拿到的证据（如实写）

| 缺什么 | 为什么 | 谁补、什么时候 |
|---|---|---|
| 本学期 ≥3 场真实用户访谈 | 需要真人，不能由 AI 编 | 角色 A，M2 评审前 |
| 真实 LLM 与规则基线的**同题对照** | 没有可用密钥；现在只证明了"可切换 + 降级" | 角色 B，拿到密钥后补一条对比表 |
| 一次真实用户测试（课件要求的活动之一） | 还没约到人 | 角色 A/C，M2 前 |
| 跨组本体评审记录 | 需要另一组参与 | 全员，M2 前 |
| 真机/模拟器截图 | `app/` 还是空的（M2 主体） | 角色 C，M2 |
