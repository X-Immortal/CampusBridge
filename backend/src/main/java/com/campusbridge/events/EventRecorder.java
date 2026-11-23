package com.campusbridge.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件记录器：内存环 + 数据库落盘双写。
 *
 * <p>数据库写失败不会影响业务（只记一次警告）：M1 的关键路径是「能演示、能解释」，
 * 而不是「表建好了才能跑」。M4 会把这里扩成正式事件表并加版本号血缘。</p>
 */
@Component
public class EventRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(EventRecorder.class);
    private static final int MAX_RECENT = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final VersionInfo versionInfo;
    private final ConcurrentLinkedDeque<AiEvent> recent = new ConcurrentLinkedDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean persistWarningLogged = new AtomicBoolean();

    public EventRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, VersionInfo versionInfo) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.versionInfo = versionInfo;
    }

    public AiEvent record(String type, String traceId, String sessionId, Map<String, Object> payload) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (payload != null) {
            enriched.putAll(payload);
        }
        if (versionInfo != null) {
            versionInfo.current().forEach(enriched::putIfAbsent);
        }
        AiEvent event = new AiEvent(
                sequence.incrementAndGet(),
                type,
                traceId,
                sessionId,
                enriched,
                Instant.now());
        recent.addFirst(event);
        while (recent.size() > MAX_RECENT) {
            recent.removeLast();
        }
        persist(event);
        return event;
    }

    public List<AiEvent> recent(int limit) {
        List<AiEvent> result = new ArrayList<>();
        for (AiEvent event : recent) {
            if (result.size() >= limit) {
                break;
            }
            result.add(event);
        }
        return result;
    }

    public List<AiEvent> bySession(String sessionId) {
        List<AiEvent> result = new ArrayList<>();
        for (AiEvent event : recent) {
            if (sessionId == null || sessionId.equals(event.sessionId())) {
                result.add(event);
            }
        }
        return result;
    }

    private void persist(AiEvent event) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_event (seq, event_type, trace_id, session_id, payload, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    event.seq(),
                    event.type(),
                    event.traceId(),
                    event.sessionId(),
                    toJson(event.payload()),
                    java.sql.Timestamp.from(event.createdAt()));
        } catch (RuntimeException ex) {
            if (persistWarningLogged.compareAndSet(false, true)) {
                LOG.warn("事件落盘失败，已退回仅内存模式：{}", ex.getMessage());
            }
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
