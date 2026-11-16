package com.campusbridge.agent;

import com.campusbridge.agent.dto.AnswerRequest;
import com.campusbridge.agent.dto.AssistantResponse;
import com.campusbridge.agent.dto.StartRequest;
import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.events.StreamEventLog;
import com.campusbridge.gateway.model.ModelGateway;
import com.campusbridge.gateway.model.ModelResult;
import com.campusbridge.gateway.model.ModelUnderstanding;
import com.campusbridge.policy.PolicyContext;
import com.campusbridge.policy.PolicyEngine;
import com.campusbridge.policy.PolicyResult;
import com.campusbridge.policy.PolicyViolation;
import com.campusbridge.schema.CardFactory;
import com.campusbridge.schema.ChoiceOption;
import com.campusbridge.schema.SchemaValidator;
import com.campusbridge.schema.TokenView;
import com.campusbridge.schema.UiCard;
import com.campusbridge.tool.ConfirmToken;
import com.campusbridge.tool.TokenService;
import com.campusbridge.tool.ToolGateway;
import com.campusbridge.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Orchestrator：理解 → 澄清 → 提案 → 确认 → 工具执行。
 *
 * <p>它本身不做业务判断：理解交给 {@link ModelGateway}，问什么交给 {@link ClarificationPolicy}，
 * 能不能执行交给 {@link ToolGateway}。它只负责把这几步串成一条可演示、可解释的链路，
 * 并在每一步留下事件。</p>
 */
@Component
public class AgentOrchestrator {

    private final ModelGateway modelGateway;
    private final ClarificationPolicy clarificationPolicy;
    private final CardFactory cardFactory;
    private final PolicyEngine policyEngine;
    private final TokenService tokenService;
    private final ToolGateway toolGateway;
    private final SessionStore sessions;
    private final SchemaValidator schemaValidator;
    private final EventRecorder events;
    private final StreamEventLog streamEvents;
    private final Clock clock;

    public AgentOrchestrator(
            ModelGateway modelGateway,
            ClarificationPolicy clarificationPolicy,
            CardFactory cardFactory,
            PolicyEngine policyEngine,
            TokenService tokenService,
            ToolGateway toolGateway,
            SessionStore sessions,
            SchemaValidator schemaValidator,
            EventRecorder events,
            StreamEventLog streamEvents,
            Clock clock) {
        this.modelGateway = modelGateway;
        this.clarificationPolicy = clarificationPolicy;
        this.cardFactory = cardFactory;
        this.policyEngine = policyEngine;
        this.tokenService = tokenService;
        this.toolGateway = toolGateway;
        this.sessions = sessions;
        this.schemaValidator = schemaValidator;
        this.events = events;
        this.streamEvents = streamEvents;
        this.clock = clock;
    }

    // ---- 1. 理解 -------------------------------------------------------------

    public AssistantResponse start(StartRequest request) {
        if (request == null || request.goal() == null || request.goal().isBlank()) {
            throw new AssistantException(400, "GOAL_REQUIRED", "请先说说你想做什么");
        }
        Map<String, Object> context = request.context() == null ? Map.of() : request.context();
        Long requesterId = requesterIdOf(context);
        String traceId = context.get("traceId") == null
                ? "tr-" + shortId()
                : String.valueOf(context.get("traceId"));

        AgentSession session = new AgentSession(
                "s-" + shortId(), traceId, requesterId, clock.instant());
        session.draft().put(DraftField.GOAL, request.goal().trim(), TaskDraft.Source.USER);
        sessions.save(session);
        events.record("session.started", traceId, session.id(), Map.of(
                "requesterId", String.valueOf(requesterId),
                "goalLength", request.goal().length()));

        ModelResult modelResult = modelGateway.understand(request.goal(), context, traceId, session.id());
        applyUnderstanding(session, modelResult);
        events.record("understanding.applied", traceId, session.id(), Map.of(
                "mode", modelResult.mode(),
                "degraded", modelResult.degraded(),
                "slotCount", session.draft().filledKeys().size(),
                "declaredCount", modelResult.understanding().declaredFields().size()));

        return emit(session, advance(session, modelResult.mode(), modelResult.notes()));
    }

    private void applyUnderstanding(AgentSession session, ModelResult modelResult) {
        ModelUnderstanding understanding = modelResult.understanding();
        TaskDraft draft = session.draft();
        understanding.slots().forEach((key, value) -> {
            if (DraftField.GOAL.key().equals(key)) {
                return;
            }
            DraftField field = DraftField.byKey(key);
            if (field == null) {
                return;
            }
            TaskDraft.Source source = understanding.declaredFields().contains(key)
                    ? TaskDraft.Source.USER
                    : TaskDraft.Source.INFERRED;
            draft.putIfAbsent(field, value, source);
        });
        session.setIntent(understanding.intent());
        session.setConfidence(understanding.confidence());
        session.setAssumptions(understanding.assumptions());
        session.setModelMode(modelResult.mode());
        session.setDegraded(modelResult.degraded());
    }

    // ---- 2. 澄清 -------------------------------------------------------------

    public AssistantResponse answer(String sessionId, AnswerRequest request) {
        AgentSession session = sessions.get(sessionId);
        if (AgentSession.CREATED.equals(session.state())) {
            throw new AssistantException(409, "ALREADY_CREATED", "这个会话已经建过单了，改内容请新建一次");
        }
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new AssistantException(400, "ANSWERS_REQUIRED", "没有收到任何回答");
        }
        synchronized (session) {
            for (AnswerRequest.AnswerInput input : request.answers()) {
                DraftField field = DraftField.byKey(input.field());
                if (field == null) {
                    events.record("answer.rejected", session.traceId(), session.id(),
                            Map.of("field", String.valueOf(input.field()), "reason", "unknown-field"));
                    continue;
                }
                session.draft().answer(field, input.value());
                events.record("clarify.answered", session.traceId(), session.id(),
                        Map.of("field", field.key()));
            }
            return emit(session, advance(session, session.modelMode(), List.of()));
        }
    }

    // ---- 3. 提案 -------------------------------------------------------------

    public AssistantResponse select(String sessionId, String optionId) {
        AgentSession session = sessions.get(sessionId);
        synchronized (session) {
            List<ClarificationQuestion> questions = clarificationPolicy.questionsFor(session.draft());
            if (!questions.isEmpty()) {
                throw new AssistantException(409, "STILL_CLARIFYING",
                        "还有 " + questions.size() + " 个问题没回答，先补全再选方案");
            }
            session.setSelectedOptionId(optionId == null || optionId.isBlank() ? "now" : optionId);
            String digest = cardFactory.digestOf(session.draft());
            ConfirmToken token = tokenService.issue(
                    session.id(), "createTask", session.draft().version(), digest);
            session.setTokenValue(token.value());
            session.setState(AgentSession.AWAITING_CONFIRMATION);

            PolicyResult preview = policyEngine.evaluate(
                    session.draft(), PolicyContext.understand(LocalDateTime.now(clock)));
            UiCard card = cardFactory.confirmation(
                    session.nextCardId(),
                    session.id(),
                    session.draft().version(),
                    session.draft(),
                    digest,
                    new TokenView(token.value(), token.expiresAt().toString(), token.nonce(), digest),
                    preview.messages(),
                    session.modelMode());
            events.record("confirmation.issued", session.traceId(), session.id(), Map.of(
                    "option", session.selectedOptionId(),
                    "taskVersion", session.draft().version(),
                    "paramDigest", digest));
            return emit(session, respond(session, card, List.of(), List.of()));
        }
    }

    // ---- 4. 确认 → 执行 ------------------------------------------------------

    public AssistantResponse confirm(String sessionId, String tokenValue, String idempotencyKey) {
        AgentSession session = sessions.get(sessionId);
        synchronized (session) {
            if (AgentSession.CREATED.equals(session.state()) && session.taskId() != null) {
                events.record("confirm.replay", session.traceId(), session.id(),
                        Map.of("taskId", String.valueOf(session.taskId())));
                UiCard card = progressCard(session, "已发布", "done",
                        "这次任务早前已经建好了，没有重复建单。");
                return emit(session, respond(session, card, List.of(), List.of("本次是重复确认，未产生新事实")));
            }
            events.record("confirm.requested", session.traceId(), session.id(),
                    Map.of("taskVersion", session.draft().version()));

            ToolResult result = toolGateway.execute(
                    session.id(), session.requesterId(), session.draft(),
                    tokenValue, idempotencyKey, session.traceId());

            if (result.ok()) {
                session.setTaskId(result.taskId());
                session.setState(AgentSession.CREATED);
                UiCard card = progressCard(session, "已发布", "done",
                        result.replay() ? "幂等命中：这是同一张卡重复提交，订单只有一张。" : "任务已写入业务库。");
                events.record("task.created", session.traceId(), session.id(), Map.of(
                        "taskId", String.valueOf(result.taskId()),
                        "replay", result.replay()));
                return emit(session, respond(session, card, List.of(), List.of(result.message())));
            }

            session.setState(AgentSession.AWAITING_CONFIRMATION);
            List<PolicyViolation> violations = result.violations();
            List<String> warnings = violations.isEmpty()
                    ? List.of(result.message())
                    : violations.stream().map(v -> v.ruleId() + "：" + v.message()).toList();
            UiCard card = rebuildConfirmation(session, warnings);
            return emit(session, respond(session, card, violations, "DENIED", List.of(result.message())));
        }
    }

    // ---- 5. 查询 -------------------------------------------------------------

    public AssistantResponse view(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        return respond(session, session.lastCard(), List.of(), List.of());
    }

    // ---- 6. 撤销 -------------------------------------------------------------

    /** 用户放弃：什么都不写库，只留下一条「取消」事件。撤销与取消在 2.0 里是清楚分开的两件事。 */
    public AssistantResponse cancel(String sessionId, String reason) {
        AgentSession session = sessions.get(sessionId);
        synchronized (session) {
            if (AgentSession.CREATED.equals(session.state())) {
                throw new AssistantException(409, "ALREADY_CREATED",
                        "任务已经建好了，撤销请走订单取消流程，不要在这里取消");
            }
            session.setState(AgentSession.CANCELLED);
            events.record("session.cancelled", session.traceId(), session.id(),
                    Map.of("reason", reason == null ? "user-cancel" : reason));
            UiCard card = cardFactory.progress(
                    session.nextCardId(), session.id(), session.draft().version(), null,
                    session.draft().version(), "已取消", "done",
                    reason == null ? "你取消了这次发布" : reason, session.modelMode());
            return emit(session, respond(session, card, List.of(), "OK", List.of("没有产生任何业务事实")));
        }
    }

    // ---- internals -----------------------------------------------------------

    private AssistantResponse advance(AgentSession session, String generatedBy, List<String> notes) {
        TaskDraft draft = session.draft();
        List<ClarificationQuestion> questions = clarificationPolicy.questionsFor(draft);
        session.setQuestions(questions);

        PolicyResult preview = policyEngine.evaluate(draft, PolicyContext.understand(LocalDateTime.now(clock)));
        List<String> warnings = preview.messages();

        UiCard card;
        if (!questions.isEmpty()) {
            session.setState(AgentSession.CLARIFYING);
            card = cardFactory.clarification(
                    session.nextCardId(), session.id(), draft.version(), questions,
                    warnings.stream().filter(w -> !w.startsWith("K03")).toList(),
                    generatedBy);
            events.record("clarify.asked", session.traceId(), session.id(), Map.of(
                    "fields", questions.stream().map(ClarificationQuestion::field).toList()));
        } else {
            session.setState(AgentSession.PROPOSING);
            card = cardFactory.comparison(
                    session.nextCardId(), session.id(), draft.version(), draft,
                    optionsFor(draft), warnings, generatedBy);
        }
        List<String> merged = new ArrayList<>(notes);
        if (session.degraded()) {
            merged.add("模型降级：本次理解使用规则基线（degraded=true）");
        }
        return respond(session, card, List.of(), merged);
    }

    /**
     * 把一次同步响应投影成流事件：卡片 +（可选）任务快照 + 结束。
     *
     * <p>流是只读投影，不产生业务事实；{@link #view(String)} 不调用它，
     * 避免「打开会话」被当成一次新动作重复推送。</p>
     */
    private AssistantResponse emit(AgentSession session, AssistantResponse response) {
        if (streamEvents.hasEnded(session.id())) {
            // 流已经收尾：重复确认、再次查看都不该往旧流里追加事件
            return response;
        }
        if (response.card() != null) {
            streamEvents.append(session.id(), "schema.card", Map.of("card", response.card()));
        }
        if (response.taskId() != null) {
            streamEvents.append(session.id(), "task.snapshot", Map.of(
                    "taskId", response.taskId(),
                    "taskStatus", "OPEN",
                    "taskVersion", session.draft().version()));
        }
        String reason = "done";
        if (AgentSession.CANCELLED.equals(session.state())) {
            reason = "cancelled";
        }
        // 只有会话真正收尾（建单成功 / 用户撤销）才发 stream.end；
        // 澄清、比较、待确认都保持流打开，等下一次动作继续推。
        boolean terminal = AgentSession.CREATED.equals(session.state())
                || AgentSession.CANCELLED.equals(session.state());
        if (terminal && !streamEvents.hasEnded(session.id())) {
            streamEvents.append(session.id(), "stream.end", Map.of("reason", reason));
        }
        return response;
    }

    private List<ChoiceOption> optionsFor(TaskDraft draft) {        List<ChoiceOption> options = new ArrayList<>();
        if ("FRIEND_FIRST".equalsIgnoreCase(draft.text(DraftField.CONTACT_PREFERENCE))) {
            options.add(new ChoiceOption("friend", "熟人优先", "先问你以前合作过的人，回得慢一点但更放心"));
            options.add(new ChoiceOption("now", "立即匹配", "对全网开放，最快被接单，但可能是陌生同学"));
        } else {
            options.add(new ChoiceOption("now", "立即匹配", "对全网开放，最快被接单，但可能是陌生同学"));
            options.add(new ChoiceOption("friend", "熟人优先", "先问你以前合作过的人，回得慢一点但更放心"));
        }
        Integer count = draft.integer(DraftField.ITEM_COUNT);
        if (count != null && count > 1) {
            options.add(new ChoiceOption("split", "拆成多单", "每单一件更容易被顺路的人接走"));
        } else {
            options.add(new ChoiceOption("scheduled", "约到时间再匹配", "到点前 30 分钟再发，减少等待"));
        }
        return options;
    }

    private UiCard rebuildConfirmation(AgentSession session, List<String> warnings) {
        String digest = cardFactory.digestOf(session.draft());
        ConfirmToken token = tokenService.issue(
                session.id(), "createTask", session.draft().version(), digest);
        session.setTokenValue(token.value());
        return cardFactory.confirmation(
                session.nextCardId(), session.id(), session.draft().version(), session.draft(),
                digest,
                new TokenView(token.value(), token.expiresAt().toString(), token.nonce(), digest),
                warnings, session.modelMode());
    }

    private UiCard progressCard(AgentSession session, String step, String state, String detail) {
        return cardFactory.progress(
                session.nextCardId(), session.id(), session.draft().version(),
                session.taskId(), session.draft().version(), step, state, detail,
                session.modelMode());
    }

    private AssistantResponse respond(
            AgentSession session, UiCard card, List<PolicyViolation> violations, List<String> notes) {
        return respond(session, card, violations, "OK", notes);
    }

    private AssistantResponse respond(
            AgentSession session, UiCard card, List<PolicyViolation> violations,
            String outcome, List<String> notes) {
        if (card != null) {
            SchemaValidator.SchemaValidation validation = schemaValidator.validate(card);
            if (!validation.valid()) {
                throw new AssistantException(500, "SCHEMA_INVALID",
                        "卡片没通过校验：" + String.join("；", validation.errors()));
            }
            session.setLastCard(card);
        }
        sessions.save(session);
        Map<String, Object> understanding = new LinkedHashMap<>();
        understanding.put("intent", session.intent());
        understanding.put("confidence", session.confidence());
        understanding.put("slots", session.draft().slots());
        return new AssistantResponse(
                session.id(),
                session.traceId(),
                session.state(),
                session.intent(),
                understanding,
                session.questions(),
                session.assumptions(),
                session.modelMode(),
                session.degraded(),
                session.taskId(),
                card,
                violations,
                outcome,
                notes);
    }

    private Long requesterIdOf(Map<String, Object> context) {
        Object raw = context.get("requesterId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return 1L;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
