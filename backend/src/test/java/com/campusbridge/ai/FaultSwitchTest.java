package com.campusbridge.ai;

import com.campusbridge.config.AiProperties;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.events.AiEvent;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.events.VersionInfo;
import com.campusbridge.gateway.model.FaultSwitch;
import com.campusbridge.gateway.model.ModelCache;
import com.campusbridge.gateway.model.ModelGateway;
import com.campusbridge.gateway.model.ModelQuota;
import com.campusbridge.gateway.model.ModelResult;
import com.campusbridge.gateway.model.ModelRouter;
import com.campusbridge.gateway.model.OpenAiCompatibleClient;
import com.campusbridge.gateway.model.RuleModelClient;
import com.campusbridge.policy.PolicyEngine;
import com.campusbridge.policy.PolicyResult;
import com.campusbridge.tool.ConfirmToken;
import com.campusbridge.tool.TaskCommandPort;
import com.campusbridge.tool.TokenService;
import com.campusbridge.tool.ToolGateway;
import com.campusbridge.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M3 三个故障开关：model_timeout 必降级、tool_fail 必 DENIED 且不落库、none 正常建单。
 */
class FaultSwitchTest {

    private final TaskCommandPort taskCommands = mock(TaskCommandPort.class);

    @Test
    void model_timeout强制降级并记录故障事件() {
        AiProperties properties = AiFixture.properties("llm");
        properties.getModel().setBaseUrl("http://127.0.0.1:1");
        properties.getModel().setApiKey("test-key");
        properties.getFault().setMode(FaultSwitch.MODEL_TIMEOUT);
        OpenAiCompatibleClient llm = mock(OpenAiCompatibleClient.class);
        when(llm.configured()).thenReturn(true);
        EventRecorder events = recorder(properties);
        ModelGateway gateway = gateway(properties, llm, events);

        ModelResult result = gateway.understand("帮我取快递", Map.of("requesterId", 1), "tr", "s");

        assertThat(result.degraded()).isTrue();
        assertThat(result.mode()).isEqualTo("rule");
        assertThat(event(events, "model.error").payload())
                .containsEntry("reason", "fault:model_timeout");
        verifyNoInteractions(llm);
    }

    @Test
    void tool_fail返回DENIED且不调用业务端口() {
        AiProperties properties = AiFixture.properties("rule");
        properties.getFault().setMode(FaultSwitch.TOOL_FAIL);
        EventRecorder events = recorder(properties);
        TokenService tokens = new TokenService(properties, AiFixture.CLOCK);
        ToolGateway gateway = toolGateway(properties, events, tokens);
        TaskDraft draft = AiFixture.completeDraft();

        ToolResult result = gateway.execute(
                "s-tool", 1L, draft, token(tokens, draft, "s-tool"), "idem-tool", "tr");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("tool_fail");
        verify(taskCommands, never()).createTask(anyLong(), any());
    }

    @Test
    void none时正常建单() {
        AiProperties properties = AiFixture.properties("rule");
        properties.getFault().setMode(FaultSwitch.NONE);
        EventRecorder events = recorder(properties);
        TokenService tokens = new TokenService(properties, AiFixture.CLOCK);
        ToolGateway gateway = toolGateway(properties, events, tokens);
        TaskDraft draft = AiFixture.completeDraft();
        when(taskCommands.createTask(any(), any())).thenReturn(42L);

        ToolResult result = gateway.execute(
                "s-ok", 1L, draft, token(tokens, draft, "s-ok"), "idem-ok", "tr");

        assertThat(result.ok()).isTrue();
        assertThat(result.taskId()).isEqualTo(42L);
        verify(taskCommands).createTask(anyLong(), any());
    }

    private ModelGateway gateway(AiProperties properties, OpenAiCompatibleClient llm, EventRecorder events) {
        return new ModelGateway(
                properties,
                new RuleModelClient(AiFixture.CLOCK),
                llm,
                events,
                new ModelRouter(properties),
                new ModelQuota(properties, AiFixture.CLOCK),
                new ModelCache(properties, AiFixture.CLOCK),
                new FaultSwitch(properties));
    }

    private ToolGateway toolGateway(AiProperties properties, EventRecorder events, TokenService tokens) {
        PolicyEngine policy = mock(PolicyEngine.class);
        when(policy.evaluate(any(), any())).thenReturn(new PolicyResult(List.of()));
        return new ToolGateway(
                tokens,
                policy,
                taskCommands,
                events,
                AiFixture.CLOCK,
                new FaultSwitch(properties));
    }

    private String token(TokenService tokens, TaskDraft draft, String sessionId) {
        ConfirmToken token = tokens.issue(sessionId, "createTask", draft.version(), "digest");
        return token.value();
    }

    private EventRecorder recorder(AiProperties properties) {
        return new EventRecorder(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                new VersionInfo(properties, AiFixture.ontology()));
    }

    private AiEvent event(EventRecorder recorder, String type) {
        return recorder.recent(100).stream()
                .filter(event -> type.equals(event.type()))
                .findFirst()
                .orElseThrow();
    }
}
