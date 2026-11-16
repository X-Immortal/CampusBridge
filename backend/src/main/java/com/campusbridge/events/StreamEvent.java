package com.campusbridge.events;

import java.util.Map;

/**
 * 流式事件信封，字段与 contract/src/transport/events.ts 的 StreamEvent 一一对应。
 *
 * <p>协议版本固定为 {@code campushub.stream.v1}。客户端只认这个信封，
 * 不认服务端内部的 {@link AiEvent}（那是审计事件，不是 UI 流）。</p>
 */
public record StreamEvent(
        String protocol,
        String eventId,
        long sequence,
        String sessionId,
        String ts,
        String kind,
        Map<String, Object> payload) {
}
