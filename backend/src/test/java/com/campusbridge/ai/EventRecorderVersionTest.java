package com.campusbridge.ai;

import com.campusbridge.config.AiProperties;
import com.campusbridge.events.AiEvent;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.events.VersionInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * M3/M4 事件版本血缘：每条事件都带五类版本，缺配置时写 unknown。
 */
class EventRecorderVersionTest {

    @Test
    void task_created事件带五类版本字段() {
        AiProperties properties = AiFixture.properties("rule");
        EventRecorder recorder = recorder(properties);

        recorder.record("task.created", "tr-version", "s-version", Map.of("taskId", "1"));
        AiEvent event = recorder.recent(1).get(0);

        assertThat(event.payload()).containsKeys(
                "modelVersion", "ontologyVersion", "promptVersion", "schemaVersion", "toolVersion");
        assertThat(event.payload().get("modelVersion")).isEqualTo("longcat");
        assertThat(event.payload().get("ontologyVersion")).isEqualTo("1.0.0");
        assertThat(event.payload().get("promptVersion")).isEqualTo("m1-prompt-v1");
        assertThat(event.payload().get("schemaVersion")).isEqualTo("1.0");
        assertThat(event.payload().get("toolVersion")).isEqualTo("m1-tools-v1");
        assertThat(event.payload().get("taskId")).isEqualTo("1");
    }

    @Test
    void 缺失配置时版本写unknown而不是丢掉字段() {
        AiProperties properties = AiFixture.properties("rule");
        properties.getModel().setName("");
        properties.setPromptVersion("");
        properties.setToolVersion(" ");
        properties.setSchemaVersion(null);
        EventRecorder recorder = recorder(properties);

        recorder.record("model.success", "tr", "s", Map.of());
        AiEvent event = recorder.recent(1).get(0);

        assertThat(event.payload().get("modelVersion")).isEqualTo("unknown");
        assertThat(event.payload().get("promptVersion")).isEqualTo("unknown");
        assertThat(event.payload().get("toolVersion")).isEqualTo("unknown");
        assertThat(event.payload().get("schemaVersion")).isEqualTo("unknown");
    }

    private EventRecorder recorder(AiProperties properties) {
        return new EventRecorder(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                new VersionInfo(properties, AiFixture.ontology()));
    }
}
