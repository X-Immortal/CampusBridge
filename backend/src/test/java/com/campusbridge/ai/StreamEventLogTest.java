package com.campusbridge.ai;

import com.campusbridge.events.StreamEvent;
import com.campusbridge.events.StreamEventLog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只测流事件日志本身：序号、信封、游标、会话隔离。 */
class StreamEventLogTest {

    @Test
    void eachSessionStartsAtOneAndStaysContiguous() {
        StreamEventLog log = new StreamEventLog();

        StreamEvent first = log.append("s-1", "text.delta", Map.of("text", "你好"));
        StreamEvent second = log.append("s-1", "schema.card", Map.of("card", Map.of("kind", "clarification")));
        StreamEvent third = log.append("s-1", "stream.end", Map.of("reason", "done"));

        assertEquals(1L, first.sequence());
        assertEquals(2L, second.sequence());
        assertEquals(3L, third.sequence());
        assertEquals("campushub.stream.v1", first.protocol());
        assertEquals("evt-s-1-1", first.eventId());
        assertNotEquals(first.eventId(), second.eventId());
        assertEquals(3L, log.lastSequence("s-1"));

        assertEquals(1L, log.append("s-2", "stream.end", Map.of("reason", "done")).sequence());
    }

    @Test
    void afterCursorReturnsOnlyGreaterSequences() {
        StreamEventLog log = new StreamEventLog();
        log.append("s-1", "text.delta", Map.of("text", "a"));
        log.append("s-1", "text.delta", Map.of("text", "b"));
        log.append("s-1", "stream.end", Map.of("reason", "done"));

        List<StreamEvent> rest = log.after("s-1", 1L);
        assertEquals(2, rest.size());
        assertEquals(2L, rest.get(0).sequence());
        assertEquals(3L, rest.get(1).sequence());

        assertTrue(log.after("s-unknown", 0L).isEmpty());
        assertTrue(log.after("s-1", 3L).isEmpty());
    }
}
