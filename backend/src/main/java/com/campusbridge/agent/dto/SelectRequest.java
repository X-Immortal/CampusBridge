package com.campusbridge.agent.dto;

/** 比较卡上选一个方案。选择只影响记录与后续策略，不改变任务内容。 */
public record SelectRequest(String optionId) {
}
