package com.campusbridge.tool;

import com.campusbridge.domain.TaskDraft;

/**
 * 业务落地的唯一出口。Agent 层不直接碰 Service / Mapper：
 * 想建单只有这一条路，路径上必须过确认令牌、策略和幂等。
 */
public interface TaskCommandPort {

    Long createTask(Long requesterId, TaskDraft draft);
}
