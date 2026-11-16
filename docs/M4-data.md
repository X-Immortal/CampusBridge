# M4 · 事件、数据治理与个人记忆

> 课件出处：00 p16、00 p17。要求：事件表 + 数据目录 + 质量规则 + 权限矩阵 + 血缘，而且**要连到真实业务使用**。

---

## 一、交互事件表（AI 交互全过程）

每条事件都要带**版本**：模型版本、本体版本、Prompt 版本、Schema 版本、工具版本。没有版本的事件表，事后无法归因。

| 事件类型 | 触发时机 | 关键字段 | 是否含个人信息 | 保留期 |
|---|---|---|---|---|
| `goal.expressed` | 用户表达目标 | goal, context, userId, traceId | 是 | |
| `intent.hypothesis` | 生成意图假设 | hypothesis, confidence, modelVersion | | |
| `clarify.question` | 追问 | field, reason, policyId | | |
| `clarify.corrected` | 用户纠正 | field, before, after | | |
| `schema.generated` | 生成 Schema | schemaVersion, cardId, blockCount | | |
| `schema.rejected` | Schema 被拒 | reason, offendingBlock | | |
| `schema.rendered` | 渲染成功 | cardId, taskVersion | | |
| `card.action` | 卡片上点了什么 | actionId, taskVersion, cardId | | |
| `tool.request` | 工具请求 | tool, paramsDigest, idempotencyKey | | |
| `confirmation.granted` | 用户确认 | action, taskVersion, nonce, expiresAt | | |
| `tool.result` | 工具结果 | ok, code, latencyMs | | |
| `tool.compensated` | 补偿 | originalEventId, reason | | |
| `task.completed` | 完成 | taskId, durationMs | | |
| `task.abandoned` | 放弃 | taskId, atStage, reason | | |
| `degrade.activated` | 降级 | level, reason | | |
| `handoff.human` | 转人工 | reason, queue | | |

**最容易漏的两类**：`schema.rejected` 和 `task.abandoned`。它们才是飞轮真正的燃料。

---

## 二、个人记忆三分

| 类型 | 存什么 | 用户能看 | 能改 | 能撤 | 到期 |
|---|---|---|---|---|---|
| **声明偏好** | 用户明确说过 | ✅ | ✅ | ✅ | 用户删除 |
| **推测偏好** | 系统推断的 | ✅ **必须标明是推测** | ✅ | ✅ | 失效或重新确认 |
| **临时上下文** | 只服务本次任务 | ✅ | — | — | **到期删除或匿名化** |

**红线**：推测偏好不得直接固化成"人格标签"（课件原话）。界面上怎么把它标出来，写在这里：

```
（贴一张截图或描述文案）
```

---

## 三、数据目录

| 数据集 | 内容 | 来源 | 更新频率 | 责任人 | 敏感级别 | 访问方式 |
|---|---|---|---|---|---|---|
| tasks | | | | | | |
| events | | | | | | |
| evals | | | | | | |
| audit | | | | | | |

---

## 四、质量规则

| # | 规则 | 检查方式 | 不达标怎么办 |
|---|---|---|---|
| 1 | 事件必须有 traceId | | |
| 2 | 确认事件必须有 nonce 且只能用一次 | | |
| 3 | 工具结果必须与幂等键一一对应 | | |
| 4 | 推测字段必须有 confirmed 标记才能进发布 | | |
| 5 | 事件里的版本字段不能为空 | | |

---

## 五、权限矩阵

| 角色 | 能读 | 能写 | 能导出 | 不能碰 |
|---|---|---|---|---|
| 需求方 | | | | |
| 服务方 | | | | |
| 第三人 | | | | **定向邀请的任务沟通** |
| 教师/管理员 | | | | |
| 模型服务 | | | | 原始身份信息 |

---

## 六、血缘

从原始事件到最终指标，画一条能走通的线：

```
用户原话 → goal.expressed → intent.hypothesis → clarify.* → schema.* → card.action
        → tool.request → confirmation.granted → tool.result → task.completed
                                                    ↓
                                          评测样本（evals/cases）
                                                    ↓
                                             飞轮实验结论
```

**评审时的问题**：这条链上某个指标变差了，你怎么定位到是哪一层？

---

## 七、自查

- [ ] 16 类事件里，我们实际记录了哪几类（列出来，别只打勾）
- [ ] 每条事件都有五类版本号
- [ ] 临时上下文真的有删除代码（不是口头说会删）
- [ ] 推测偏好在界面上确实被标出来了（有截图）
- [ ] 权限矩阵里"第三人不能读定向邀请沟通"有测试守着
