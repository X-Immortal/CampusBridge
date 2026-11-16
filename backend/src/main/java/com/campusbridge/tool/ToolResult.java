package com.campusbridge.tool;

import com.campusbridge.policy.PolicyViolation;

import java.util.List;

/** 工具执行结果。{@code replay=true} 表示「这是幂等命中，没有产生第二次事实」。 */
public record ToolResult(
        boolean ok,
        boolean replay,
        Long taskId,
        String message,
        List<PolicyViolation> violations) {

    public static ToolResult success(Long taskId, boolean replay, String message) {
        return new ToolResult(true, replay, taskId, message, List.of());
    }

    public static ToolResult denied(String message, List<PolicyViolation> violations) {
        return new ToolResult(false, false, null, message, violations);
    }
}
