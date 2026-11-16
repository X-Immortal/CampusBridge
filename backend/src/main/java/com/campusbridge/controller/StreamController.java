package com.campusbridge.controller;

import com.campusbridge.events.StreamEvent;
import com.campusbridge.events.StreamEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 只推送通道：{@code GET /api/stream?sessionId=&after=}。
 *
 * <p>实现按 contract/PROTOCOL.md §1/§4/§5：
 * 只发 {@link StreamEvent} 信封；断开重连用 after 补放；
 * 发了 {@code stream.end} 就收尾，不产生任何业务事实。</p>
 *
 * <p>M2 演示版：事件来自 {@link StreamEventLog} 的内存环，轮询 150ms 推送。</p>
 */
@RestController
public class StreamController {

    private static final long CONNECTION_TIMEOUT_MS = 120_000L;
    private static final long POLL_INTERVAL_MS = 150L;
    private static final long IDLE_HEARTBEAT_MS = 15_000L;

    private static final ExecutorService PUMP = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "campusbridge-sse");
        thread.setDaemon(true);
        return thread;
    });

    private final StreamEventLog events;
    private final ObjectMapper objectMapper;

    public StreamController(StreamEventLog events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "0") long after) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        PUMP.execute(() -> pump(sessionId, after, emitter));
        return emitter;
    }

    private void pump(String sessionId, long after, SseEmitter emitter) {
        long cursor = Math.max(after, 0L);
        long startedAt = System.currentTimeMillis();
        long lastWriteAt = startedAt;

        try {
            while (System.currentTimeMillis() - startedAt < CONNECTION_TIMEOUT_MS) {
                List<StreamEvent> batch = events.after(sessionId, cursor);
                if (!batch.isEmpty()) {
                    for (StreamEvent event : batch) {
                        String json = objectMapper.writeValueAsString(event);
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(event.sequence()))
                                .name("message")
                                .data(json, MediaType.TEXT_PLAIN));
                        cursor = event.sequence();
                        lastWriteAt = System.currentTimeMillis();
                        if ("stream.end".equals(event.kind())) {
                            emitter.complete();
                            return;
                        }
                    }
                } else if (System.currentTimeMillis() - lastWriteAt >= IDLE_HEARTBEAT_MS) {
                    emitter.send(SseEmitter.event().comment("ping"));
                    lastWriteAt = System.currentTimeMillis();
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            emitter.complete();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception error) {
            emitter.completeWithError(error);
        }
    }
}
