package com.campusbridge.gateway.model;

/** 网关返回给 Agent 的结果：理解内容 + 这次用的是哪个模式 + 是否降级。 */
public record ModelResult(
        ModelUnderstanding understanding,
        String mode,
        boolean degraded,
        java.util.List<String> notes) {
}
