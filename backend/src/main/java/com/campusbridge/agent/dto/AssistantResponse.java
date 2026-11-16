package com.campusbridge.agent.dto;

import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.policy.PolicyViolation;
import com.campusbridge.schema.UiCard;

import java.util.List;
import java.util.Map;

/**
 * 一次调用的统一返回信封。
 *
 * <p>关键字段：{@code state} 是会话状态机；{@code unknowns} 是这次真正问出口的问题；
 * {@code assumptions} 是系统替用户补的假设（必须让它可见）；{@code degraded} 表示模型降级过。</p>
 */
public record AssistantResponse(
        String sessionId,
        String traceId,
        String state,
        String intent,
        Map<String, Object> understanding,
        List<ClarificationQuestion> unknowns,
        List<String> assumptions,
        String modelMode,
        boolean degraded,
        Long taskId,
        UiCard card,
        List<PolicyViolation> violations,
        String outcome,
        List<String> notes) {
}
