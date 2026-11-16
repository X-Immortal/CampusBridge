package com.campusbridge.ai;

import com.campusbridge.agent.AgentOrchestrator;
import com.campusbridge.agent.ClarificationPolicy;
import com.campusbridge.agent.SessionStore;
import com.campusbridge.agent.dto.AnswerRequest;
import com.campusbridge.agent.dto.AssistantResponse;
import com.campusbridge.agent.dto.StartRequest;
import com.campusbridge.config.AiProperties;
import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.gateway.model.ModelGateway;
import com.campusbridge.gateway.model.OpenAiCompatibleClient;
import com.campusbridge.gateway.model.RuleModelClient;
import com.campusbridge.ontology.OntologyRegistry;
import com.campusbridge.policy.PolicyEngine;
import com.campusbridge.schema.CardFactory;
import com.campusbridge.schema.SchemaValidator;
import com.campusbridge.tool.TaskCommandPort;
import com.campusbridge.tool.TokenService;
import com.campusbridge.tool.ToolGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 整条链路：理解 → 追问（可能多轮）→ 比较 → 确认 → 建单 → 重复确认不重复建单。
 * 这条测试就是评审那天要演的那条路径。
 */
class AgentFlowTest {

    private final EventRecorder events = mock(EventRecorder.class);
    private final TaskCommandPort taskCommands = mock(TaskCommandPort.class);
    private final com.campusbridge.events.StreamEventLog streamEvents =
            new com.campusbridge.events.StreamEventLog();

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(taskCommands.createTask(any(), any())).thenReturn(99L);
        orchestrator = build(AiFixture.properties("rule"));
    }

    @Test
    void 缺信息时先追问_补全后出比较卡() {
        AssistantResponse start = orchestrator.start(new StartRequest("帮我取个快递", Map.of()));
        assertThat(start.card().kind()).isEqualTo("clarification");
        assertThat(start.unknowns()).hasSize(3);
        assertThat(start.card().blocks()).anyMatch(b -> "Field".equals(b.type()));

        AssistantResponse after = answerAllRounds(start);
        assertThat(after.card().kind()).isEqualTo("comparison");
        assertThat(after.card().blocks()).anyMatch(b -> "Choice".equals(b.type()));
        assertThat(after.state()).isEqualTo("PROPOSING");
    }

    @Test
    void 确认后建单_重复确认不再建单() {
        AssistantResponse start = orchestrator.start(new StartRequest("帮我取个快递", Map.of()));
        AssistantResponse comparison = answerAllRounds(start);
        AssistantResponse confirmation = orchestrator.select(comparison.sessionId(), "now");

        assertThat(confirmation.card().kind()).isEqualTo("confirmation");
        assertThat(confirmation.card().token().value()).isNotBlank();
        assertThat(confirmation.state()).isEqualTo("AWAITING_CONFIRMATION");

        AssistantResponse created = orchestrator.confirm(
                confirmation.sessionId(), confirmation.card().token().value(), "idem-1");
        assertThat(created.outcome()).isEqualTo("OK");
        assertThat(created.taskId()).isEqualTo(99L);
        assertThat(created.card().kind()).isEqualTo("progress");
        assertThat(created.state()).isEqualTo("CREATED");
        verify(taskCommands, times(1)).createTask(any(), any());

        AssistantResponse replay = orchestrator.confirm(
                confirmation.sessionId(), confirmation.card().token().value(), "idem-1");
        assertThat(replay.outcome()).isEqualTo("OK");
        assertThat(replay.taskId()).isEqualTo(99L);
        verify(taskCommands, times(1)).createTask(any(), any());
    }

    @Test
    void 改过内容之后旧确认卡失效() {
        AssistantResponse start = orchestrator.start(
                new StartRequest("今晚九点去北门菜鸟驿站帮我取3个快递，预算8元", Map.of()));
        assertThat(start.card().kind()).isEqualTo("comparison");

        AssistantResponse confirmation = orchestrator.select(start.sessionId(), "now");
        String oldToken = confirmation.card().token().value();

        // 只改一个「合法」字段：把报酬从 8 改成 9。除版本之外没有任何违规，
        // 所以如果还能建单，就说明版本绑定没起作用。
        AssistantResponse changed = orchestrator.answer(start.sessionId(),
                new AnswerRequest(List.of(new AnswerRequest.AnswerInput("rewardAmount", 9))));
        assertThat(changed.card().kind()).isEqualTo("comparison");

        AssistantResponse denied = orchestrator.confirm(start.sessionId(), oldToken, "idem-old");
        assertThat(denied.outcome()).isEqualTo("DENIED");
        assertThat(denied.violations()).extracting("ruleId").contains("K13");
        assertThat(denied.violations()).extracting("ruleId").doesNotContain("K01");
        verify(taskCommands, times(0)).createTask(anyLong(), any());
    }

    @Test
    void 模型不可用时降级但仍然能出卡片() {
        AgentOrchestrator degraded = build(AiFixture.properties("llm"));
        AssistantResponse response = degraded.start(new StartRequest("帮我取个快递", Map.of()));
        assertThat(response.degraded()).isTrue();
        assertThat(response.modelMode()).isEqualTo("rule");
        assertThat(response.notes()).anyMatch(n -> n.contains("降级") || n.contains("规则基线"));
        assertThat(response.card()).isNotNull();
    }

    @Test
    void 流事件覆盖五种kind且序号连续() {
        AssistantResponse start = orchestrator.start(new StartRequest("帮我取个快递", Map.of()));
        AssistantResponse comparison = answerAllRounds(start);
        AssistantResponse confirmation = orchestrator.select(comparison.sessionId(), "now");
        orchestrator.confirm(
                confirmation.sessionId(), confirmation.card().token().value(), "idem-stream");

        List<com.campusbridge.events.StreamEvent> stream =
                streamEvents.after(start.sessionId(), 0L);
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (int i = 0; i < stream.size(); i++) {
            kinds.add(stream.get(i).kind());
            assertThat(stream.get(i).sequence()).isEqualTo(i + 1L);
        }
        assertThat(kinds).contains(
                "text.delta", "schema.card", "tool.progress", "task.snapshot", "stream.end");
    }

    private AssistantResponse answerAllRounds(AssistantResponse start) {
        AssistantResponse current = start;
        for (int round = 0; round < 4 && "clarification".equals(current.card().kind()); round++) {
            List<AnswerRequest.AnswerInput> answers = new ArrayList<>();
            for (ClarificationQuestion question : current.unknowns()) {
                answers.add(new AnswerRequest.AnswerInput(question.field(), valueFor(question.field())));
            }
            current = orchestrator.answer(current.sessionId(), new AnswerRequest(answers));
        }
        return current;
    }

    private Object valueFor(String field) {
        return switch (field) {
            case "deadline" -> "2026-11-05T21:00";
            case "location" -> "北门菜鸟驿站";
            case "rewardType" -> "CASH";
            case "rewardAmount" -> 8;
            case "rewardDescription" -> "请喝一杯咖啡";
            case "completionStandard" -> "放到一层货架并拍照确认完成";
            case "riskDisclosureConfirmed" -> true;
            case "itemCount" -> 3;
            case "title" -> "帮取快递到宿舍楼下";
            default -> "补充值";
        };
    }

    private AgentOrchestrator build(AiProperties properties) {
        ObjectMapper mapper = new ObjectMapper();
        RuleModelClient ruleClient = new RuleModelClient(AiFixture.CLOCK);
        OpenAiCompatibleClient llmClient = new OpenAiCompatibleClient(properties, mapper);
        ModelGateway modelGateway = new ModelGateway(properties, ruleClient, llmClient, events);

        OntologyRegistry ontology = AiFixture.ontology();
        PolicyEngine policyEngine = AiFixture.policyEngine(ontology);
        TokenService tokenService = new TokenService(properties, AiFixture.CLOCK);
        ToolGateway toolGateway = new ToolGateway(
                tokenService, policyEngine, taskCommands, events, AiFixture.CLOCK);
        return new AgentOrchestrator(
                modelGateway,
                new ClarificationPolicy(),
                new CardFactory(),
                policyEngine,
                tokenService,
                toolGateway,
                new SessionStore(),
                new SchemaValidator(),
                events,
                streamEvents,
                AiFixture.CLOCK);
    }
}
