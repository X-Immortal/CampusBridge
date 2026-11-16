# M1 现场演示脚本

> 目标：**8 分钟内**证明四件事 —— 会追问、会出卡、确认才写库、规则能现场改。
> 每一步都有命令和预期输出，练到不用看这份文档为止。

---

## 0. 准备（一次，两分钟）

```powershell
# 起服务：H2 内存库 + 规则基线 + 本体读源文件（为了能现场改）
cd backend
mvn -o spring-boot:run `
  "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true" `
  "-Dspring-boot.run.jvmArguments=-Dai.ontology.location=file:./src/main/resources/ontology/campus-ontology.v1.json"
```

- 打开 <http://127.0.0.1:8081/ai-demo/> 就是对话原型；
- `GET /api/ontology` 能看到本体指纹与 20 / 23 / 15 的计数；
- 数据是内存库，每次重启从干净状态开始 —— **演示不要用生产库**。

> 为什么加 `useTestClasspath`：H2 目前是 test 依赖，演示库跑在 H2 上。
> 生产（MySQL）用 `backend/sql/schema.sql`，不需要这个参数。

---

## 1. 会追问（60 秒）

输入：

> 帮我取个快递

预期：

- 卡片类型 `clarification`，**只问三个问题**：最晚几点、去哪取、怎么答谢；
- 每一个 `Field` 都带 `hint`，说明"为什么问"；
- 响应里 `unknowns` 三条、`taskId=null`、`state=CLARIFYING`。

一句话解释：**它没有把"今晚 9 点""北门"这些没说过的东西编出来。**

---

## 2. 补全 → 比较 → 确认（90 秒）

按卡填写：`21:00`、`北门菜鸟驿站`、`现金 8`。

预期：

1. 先出 `comparison`（立即匹配 / 熟人优先 / 约到时间再匹配）；
2. 选"立即匹配"→ 出 `confirmation`，摘要每行右边标着「用户说的」或「系统推测」；
3. 点"确认发布"→ 出 `progress`，任务编号 `#1`。

验证真的落库了（另开一个窗口）：

```powershell
curl.exe -s "http://127.0.0.1:8081/api/events?sessionId=<你的 sessionId>&limit=40"
```

会看到 `confirmation.issued → confirm.requested → tool.request → tool.success → task.created` 五条。

---

## 3. 重复点击不重复建单（30 秒）

再点一次"确认发布"，或用同一张卡重发：

预期：还是 `#1`，返回 `notes=["本次是重复确认，未产生新事实"]`，事件里多一条 `tool.idempotent.replay`。

---

## 4. 旧卡失效（60 秒）

1. 重新走一次流程，拿到确认卡（先别点确认）；
2. 回到对话改一处：把报酬改成 9；
3. 再点**旧的那张卡**的"确认发布"。

预期：HTTP 409，`violations` 里是 `K13：确认卡已过期：令牌绑定版本 15，任务已经改到版本 16`。

---

## 5. 现场改一条约束（90 秒，最值钱的一段）

```powershell
# 1. 把 K09 的标题下限从 5 改成 20
$path = 'backend/src/main/resources/ontology/campus-ontology.v1.json'
$json = [IO.File]::ReadAllText($path)
$json = $json.Replace('"params": {"min": 5, "max": 100}', '"params": {"min": 20, "max": 100}')
[IO.File]::WriteAllText($path, $json, (New-Object Text.UTF8Encoding($false)))

# 2. 不重启，直接刷新本体
curl.exe -s -X POST http://127.0.0.1:8081/api/ontology/reload
```

然后走一次完整流程并确认。

预期：**409**，`violations` 里是 `K09：标题要 20–100 字（当前 10 字）`。
把 JSON 改回去再 reload，同一张卡就又能建单。

一句话解释：**改的是数据里的规则，不是代码里的 if。**

---

## 6. 模型关掉也能演（60 秒）

在请求里加 `context.modelMode = "llm"`（等价于把模型配置指到一个坏地址）：

```powershell
curl.exe -s -X POST http://127.0.0.1:8081/api/assistant `
  -H "Content-Type: application/json" `
  -d '{"goal":"今晚九点去北门菜鸟驿站帮我取3个快递，预算8元","context":{"modelMode":"llm"}}'
```

预期：`modelMode=rule`、`degraded=true`、卡片照常出，事件里有 `model.fallback`。

---

## 7. 跑评测集（30 秒）

```powershell
node evals/run.mjs --live http://127.0.0.1:8081
```

预期：18/18 通过，八类覆盖全绿，报告写进 `evals/reports/`。

**这一段替代"我们测过了"这种口头保证。**

---

## 8. 如果被问住：三句实话

1. **模型还是规则？** 两种都实现了，模式可切；现在演示走规则基线，因为没配密钥，而且规则版可测。
2. **对话状态在哪？** M1 在进程内存里，重启即失效；M2 用 `lastStableVersion` + 游标补恢复。
3. **本体真的在起作用吗？** 第 5 步就是证据；另外 `PolicyEngineTest` 有 19 条，一条对应一条约束。

---

## 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动报 `Cannot load driver class: org.h2.Driver` | 忘了 `useTestClasspath=true` | 补上参数重跑 |
| 端口 8081 被占 | 上一次的 Java 进程还在 | `Get-NetTCPConnection -LocalPort 8081`，杀掉 |
| `/ai-demo/` 404 | 静态资源没重新编译 | `mvn -o compile` 后重启 |
| reload 没生效 | 本体路径不是 `file:` | 检查启动参数里的 `ai.ontology.location` |
| 确认老是被 K13 拒 | 手里拿的是旧卡 | 重新选一次方案，用新卡上的令牌 |
