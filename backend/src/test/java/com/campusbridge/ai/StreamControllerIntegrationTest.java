package com.campusbridge.ai;

import com.campusbridge.events.StreamEventLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/stream 的契约测试：只推 StreamEvent 信封、after 游标生效、stream.end 后收尾。
 * 不碰业务表，事件直接塞进 StreamEventLog。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StreamEventLog events;

    @Test
    void replaysRecordedFramesAndClosesOnStreamEnd() throws Exception {
        String sessionId = "s-stream-" + UUID.randomUUID();
        events.append(sessionId, "schema.card", Map.of("card", Map.of("kind", "clarification")));
        events.append(sessionId, "text.delta", Map.of("text", "稍等"));
        events.append(sessionId, "stream.end", Map.of("reason", "done"));

        MvcResult result = mockMvc.perform(get("/api/stream")
                        .param("sessionId", sessionId)
                        .param("after", "0"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000L);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
        assertThat(body).contains("schema.card").contains("text.delta").contains("stream.end");
        assertThat(body).contains("\"sequence\":1").contains("\"sequence\":2").contains("\"sequence\":3");
    }

    @Test
    void afterCursorSkipsAlreadyRenderedEvents() throws Exception {
        String sessionId = "s-stream-" + UUID.randomUUID();
        events.append(sessionId, "text.delta", Map.of("text", "a"));
        events.append(sessionId, "text.delta", Map.of("text", "b"));
        events.append(sessionId, "stream.end", Map.of("reason", "done"));

        MvcResult result = mockMvc.perform(get("/api/stream")
                        .param("sessionId", sessionId)
                        .param("after", "2"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000L);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("\"sequence\":1").doesNotContain("\"sequence\":2");
        assertThat(body).contains("\"sequence\":3").contains("stream.end");
    }
}
