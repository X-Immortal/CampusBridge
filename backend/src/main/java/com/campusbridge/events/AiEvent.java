package com.campusbridge.events;

import java.time.Instant;
import java.util.Map;

/**
 * 一条交互事件。M1 先落最关键的几类；M4 会补齐到课件要求的 16 类并带全版本号。
 *
 * @param type 事件类型，例如 {@code session.started} / {@code clarify.asked} / {@code tool.success}
 */
public record AiEvent(
        long seq,
        String type,
        String traceId,
        String sessionId,
        Map<String, Object> payload,
        Instant createdAt) {
}
