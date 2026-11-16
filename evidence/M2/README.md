# M2 · 客户端证据（模拟器实拍）

> 2026-11-16 18:32–18:38，DevEco 模拟器（Phone / API 22，`hdc` 目标 `127.0.0.1:5555`）。
> 后端：`mvn -o spring-boot:run -Dspring-boot.run.profiles=demo`（H2 + 规则基线）；
> 端口反代：`scripts/emulator-net.ps1`（`hdc rport` 4390 / 8081）。

## 截图

| 文件 | 拍的是什么 |
|---|---|
| `screenshots/01-card-clarification.png` | 首页「澄清」页签：缺件数/时间/取件点/报酬时出澄清卡，带来源标记与必填提示 |
| `screenshots/02-card-comparison.png` | 「比较」页签：三种找人方式 + 最早送达 / 平均等待（系统推测标黄） |
| `screenshots/03-card-confirmation.png` | 「确认」页签：目标/取件点/截止/报酬摘要，副作用按钮橙色 + 令牌 |
| `screenshots/04-card-progress.png` | 「进度」页签：已发布 / 等待接单 / 送达确认，任务号 T-1024 |
| `screenshots/05-bad-card-rejected.png` | 「坏卡」页签：`Script` 组件与缺确认的 `confirm` 被拒，卡片下方列出拒绝原因 |
| `screenshots/06-real-link-e2e.png` | 真实链路页：`确认结果：OK · taskId=2`，`流事件 14 条`（五种 kind 齐全） |

## 复现

```powershell
# 1. 起后端（demo profile，H2，规则基线）
cd D:\MWSE-2026\CampusBridge\backend
mvn -o spring-boot:run "-Dspring-boot.run.profiles=demo" `
  "-Dspring-boot.run.useTestClasspath=true" `
  "-Dspring-boot.run.jvmArguments=-Dai.ontology.location=file:./src/main/resources/ontology/campus-ontology.v1.json"

# 2. 端口反代给设备
cd ..
powershell -ExecutionPolicy Bypass -File .\scripts\emulator-net.ps1

# 3. 装 HAP 并启动
hdc install -r .\app\entry\build\default\outputs\default\entry-default-unsigned.hap
hdc shell aa start -a EntryAbility -b edu.nju.campusbridge2
```

进首页点「真实链路：一句话 → 追问 → 确认 → 建单」，等 10 秒左右，页面会显示
`确认结果：OK · taskId=N` 和 14 条流事件；返回首页可切四类卡片与坏卡页签。

## 说明

- 模拟器接受**未签名 debug HAP**（`signingConfigs` 为空也能装）；真机安装才需要华为账号自动签名。
- 截图里的卡片数据来自 `fixtures/Fixtures.ets`，用于演示四类渲染；真实链路页的卡片与流事件来自后端。
- 已知小问题：`router.pushUrl/back` 在 API 22 上被标记 deprecated（仍可用），后续可换 `Navigation`。
