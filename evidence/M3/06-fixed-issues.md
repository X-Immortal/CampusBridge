# M3 实测中发现并修复的问题

## 1. 任务详情页在 null 字段上崩溃

- 现象：模拟器点任务 → App 直接退出；`aa dump -l` 无 mission；hilog 出现
  `jscrash-edu.nju.campusbridge2-...log`。
- 根因：`TaskDetailPanel.ets` 的 `fieldRow` 只判断 `undefined`，但 `TaskView.deliveryLocation`
  等字段实际返回 `null`；`value.length` 抛 `TypeError: Cannot read property length of null`
  （crash log 行 `TaskDetailPanel.ets:49:18`）。
- 修复：`ui/taskApi.ts` 增加 `textOf(value: string | null | undefined)`，`fieldRow` 和列表
  的 location/deadline 全部走 `textOf`；`rewardText/statusText` 同步处理 null。
- 验证：重新 `assembleHap` 299 KB，安装后点任务进入详情，`04-task-detail.json` 出现
  `任务详情 / 报酬 / 截止时间 / 完成标准 / 演示同学`，App 保持前台。

## 2. demo schema 缺 `user_profiles`，任务列表 500

- 现象：App 探针 `GET /api/v1/tasks?page=1&size=1` 返回 500
  `COMMON_999_INTERNAL_ERROR`；后端日志 `Table "user_profiles" not found`。
- 根因：`backend/src/main/resources/db/demo-schema.sql` 只建了 assistant 链路的最小表，
  但 `TaskMapper.xml` 的公开列表/详情查询会 `LEFT JOIN user_profiles`。
- 修复：demo schema 增加 `user_profiles`（与 `deploy/mysql/schema.sql` 对齐的字段子集），
  demo data 为 `demo_student` 插入一条昵称；重启 demo 后列表 200，App 显示 `共 1 条`。
- 验证：`03-task-list.json` / `04-task-detail.json`；`evals/reports/load-*.json` 仍全绿。

## 3. 红队文档的复现命令缺 test classpath

- 现象：照 `docs/M4-redteam.md` 的 `mvn spring-boot:run` 起 demo 会报
  `Cannot load driver class: org.h2.Driver`。
- 根因：`h2` 在 `pom.xml` 里是 `test` scope，`spring-boot:run` 默认不带测试类路径。
- 修复：命令补 `-Dspring-boot.run.useTestClasspath=true`，并加一行说明。
- 验证：按新命令起 demo 后 `node evals/redteam/run.mjs --base ...` 5/5，退出码 0。
