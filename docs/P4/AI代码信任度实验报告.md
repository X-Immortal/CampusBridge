# AI 代码信任度实验报告

## 实验基本信息

- 实验主题：任务状态流转逻辑
- 选择原因：任务状态机是 CampusBridge 的核心业务规则，错误流转会直接影响接单、完成确认、争议和评价。

## 实验目标

观察 AI 直接生成的“任务状态更新”代码是否能直接进入项目，以及人工审查在业务约束、边界条件和工程一致性上的必要性。

## Prompt

```text
请基于 Spring Boot + MyBatis 为校园互助平台生成一个任务状态流转方法。
任务状态包括 OPEN、ACCEPTED、IN_PROGRESS、PENDING_CONFIRMATION、COMPLETED、CANCELLED、DISPUTED。
支持动作 START、CONFIRM_COMPLETION、REJECT_COMPLETION、DISPUTE、CANCEL。
要求校验操作者身份：发布者可以取消、确认、驳回；接单者可以开始和提交完成；双方可以争议。
非法状态流转要抛出业务异常，并记录任务历史。
```

## AI 直出结果摘要

AI 给出的核心逻辑包括：

- 使用 `switch(action)` 判断目标状态
- 对开始任务、确认完成、驳回、争议、取消做基本状态判断
- 在状态变更后调用 Mapper 更新任务
- 简单插入一条任务历史记录

直出代码的优点：

- 主流程结构清晰，能快速形成状态机雏形
- 对大部分动作给出了起始状态和目标状态
- 代码便于人工继续补充权限和异常码

直出代码的主要问题：

| 问题 | 具体表现 | 风险 |
|---|---|---|
| 权限判断不完整 | 只判断了 requesterId，未完整区分 providerId | 接单者或发布者可能执行错误动作 |
| 状态枚举与项目异常码不一致 | AI 使用普通字符串和 RuntimeException | 不符合项目统一 `ErrorCode` 和 `Result` 响应 |
| 历史记录信息不足 | 未记录 fromStatus、toStatus、actorId、reason | 争议仲裁时缺少可追溯证据 |
| 未考虑通知触发 | 状态变化后没有通知对方 | 前端消息中心无法展示关键进展 |

## 人工审查与修复

人工修复后采用项目内已有的 `TaskServiceImpl`、`TaskHistoryService`、`NotificationService` 和统一异常体系：

- 使用 `ApiEnums.TaskStatus` 和 `ApiEnums.TaskStatusTransitionAction`
- 将动作校验拆分到 `handleStart`、`handleConfirmCompletion`、`handleRejectCompletion`、`handleDispute`、`handleCancel`
- 对 requester/provider 分别校验权限
- 每次状态变更都记录任务历史
- 关键事件触发系统通知
- 驳回、争议、取消要求填写原因

## 对比表

| 指标 | AI 直出                   | 人工审查修复后 |
|---|-------------------------|---|
| 编译是否通过 | 否，需要适配项目 DTO、枚举和 Mapper | 是 |
| 功能是否可运行 | 只能覆盖部分情况                | 可覆盖开始、确认、驳回、争议、取消 |
| 测试是否通过 | 未通过现有服务测试               | `BE04BE08TaskServiceImplTest` 通过 |
| 主要问题/修复说明	 | 普通异常，响应格式不统一            | 使用统一业务错误码 |