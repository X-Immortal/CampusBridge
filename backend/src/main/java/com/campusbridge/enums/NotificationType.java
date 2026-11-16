package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    POST_REPLY(ContentItemType.POST, "帖子收到新回复"),
    TASK_APPLICATION_CREATED(ContentItemType.TASK, "收到接单申请"),
    TASK_APPLICATION_ACCEPTED(ContentItemType.TASK, "接单申请已通过"),
    TASK_APPLICATION_REJECTED(ContentItemType.TASK, "接单申请未被选中"),
    TASK_APPLICATION_WITHDRAWN(ContentItemType.TASK, "接单申请已撤回"),
    TASK_PUBLISHED(ContentItemType.TASK, "任务已开放接单"),
    TASK_PENDING_REVIEW(ContentItemType.TASK, "任务等待审核"),
    TASK_ACCEPTED(ContentItemType.TASK, "任务已选定帮助者"),
    TASK_STARTED(ContentItemType.TASK, "任务正在进行"),
    TASK_PENDING_CONFIRMATION(ContentItemType.TASK, "任务等待完成确认"),
    TASK_COMPLETED(ContentItemType.TASK, "任务已完成"),
    TASK_REVIEWED(ContentItemType.TASK, "收到任务评价"),
    TASK_CANCELLED(ContentItemType.TASK, "任务已取消"),
    TASK_DISPUTED(ContentItemType.TASK, "任务进入争议处理"),
    TASK_REJECTED(ContentItemType.TASK, "任务审核未通过"),
    TASK_OPEN_TIMEOUT(ContentItemType.TASK, "任务已超过接单截止时间"),
    TASK_CONFIRMATION_REMINDER(ContentItemType.TASK, "请确认任务是否完成");

    private final ContentItemType relatedType;
    private final String defaultTitle;
}
