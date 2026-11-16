package com.campusbridge.events;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个会话一条流：序号从 1 开始单调 +1，供 {@code GET /api/stream?sessionId=&after=} 重放。
 *
 * <p>M2 演示阶段先放内存；进程重启后流从头开始，客户端用 {@code cursor_expired}
 * 或全量刷新兜底。M4 再把这里接到正式事件表（ai_event 已有落盘）。</p>
 */
@Component
public class StreamEventLog {

    public static final String PROTOCOL = "campushub.stream.v1";

    private final Map<String, SessionStream> streams = new ConcurrentHashMap<>();

    /** 追加一条事件并返回它；sequence 由本会话自己维护。 */
    public StreamEvent append(String sessionId, String kind, Map<String, Object> payload) {
        SessionStream stream = streams.computeIfAbsent(sessionId, key -> new SessionStream());
        return stream.append(sessionId, kind, payload);
    }

    /** 取 sequence > after 的事件（按序号升序）。 */
    public List<StreamEvent> after(String sessionId, long after) {
        SessionStream stream = streams.get(sessionId);
        if (stream == null) {
            return List.of();
        }
        return stream.after(after);
    }

    /** 该会话最后一条事件的序号；没有事件时为 0。 */
    public long lastSequence(String sessionId) {
        SessionStream stream = streams.get(sessionId);
        return stream == null ? 0L : stream.lastSequence();
    }

    /** 该会话是否已经发过 stream.end（避免重复确认时追加第二条结束帧）。 */
    public boolean hasEnded(String sessionId) {
        SessionStream stream = streams.get(sessionId);
        return stream != null && "stream.end".equals(stream.lastKind());
    }

    public void clear(String sessionId) {
        streams.remove(sessionId);
    }

    private static final class SessionStream {
        private final List<StreamEvent> events = new ArrayList<>();
        private long sequence = 0L;

        synchronized StreamEvent append(String sessionId, String kind, Map<String, Object> payload) {
            sequence += 1;
            StreamEvent event = new StreamEvent(
                    PROTOCOL,
                    "evt-" + sessionId + "-" + sequence,
                    sequence,
                    sessionId,
                    Instant.now().toString(),
                    kind,
                    payload == null ? Map.of() : payload);
            events.add(event);
            return event;
        }

        synchronized List<StreamEvent> after(long after) {
            List<StreamEvent> result = new ArrayList<>();
            for (StreamEvent event : events) {
                if (event.sequence() > after) {
                    result.add(event);
                }
            }
            return result;
        }

        synchronized long lastSequence() {
            return sequence;
        }

        synchronized String lastKind() {
            return events.isEmpty() ? "" : events.get(events.size() - 1).kind();
        }
    }
}
