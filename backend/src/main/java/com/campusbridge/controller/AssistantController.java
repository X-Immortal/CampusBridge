package com.campusbridge.controller;

import com.campusbridge.agent.AgentOrchestrator;
import com.campusbridge.agent.dto.AnswerRequest;
import com.campusbridge.agent.dto.AssistantResponse;
import com.campusbridge.agent.dto.ConfirmRequest;
import com.campusbridge.agent.dto.SelectRequest;
import com.campusbridge.agent.dto.StartRequest;
import com.campusbridge.events.AiEvent;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.ontology.OntologyRegistry;
import com.campusbridge.schema.SchemaValidator;
import com.campusbridge.schema.UiCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1 的四个入口。它们只做协议转换，不做业务判断：
 * 理解/追问/策略/工具全在各自组件里，这里能少一行就少一行。
 */
@RestController
@RequestMapping("/api")
public class AssistantController {

    private final AgentOrchestrator orchestrator;
    private final OntologyRegistry ontology;
    private final SchemaValidator schemaValidator;
    private final EventRecorder events;
    private final ObjectMapper objectMapper;

    public AssistantController(
            AgentOrchestrator orchestrator,
            OntologyRegistry ontology,
            SchemaValidator schemaValidator,
            EventRecorder events,
            ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.ontology = ontology;
        this.schemaValidator = schemaValidator;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** 一句话进来：能发就出比较卡，不能发就出澄清卡。 */
    @PostMapping("/assistant")
    public AssistantResponse start(@RequestBody StartRequest request) {
        return orchestrator.start(request);
    }

    @PostMapping("/assistant/sessions/{sessionId}/answers")
    public AssistantResponse answer(
            @PathVariable String sessionId, @RequestBody AnswerRequest request) {
        return orchestrator.answer(sessionId, request);
    }

    @PostMapping("/assistant/sessions/{sessionId}/select")
    public AssistantResponse select(
            @PathVariable String sessionId, @RequestBody SelectRequest request) {
        return orchestrator.select(sessionId, request == null ? null : request.optionId());
    }

    /** 确认：成功 200；被规则拦住 409，body 里带 ruleId 与原因，不是一句「失败了」。 */
    @PostMapping("/assistant/sessions/{sessionId}/confirm")
    public ResponseEntity<AssistantResponse> confirm(
            @PathVariable String sessionId, @RequestBody ConfirmRequest request) {
        AssistantResponse response = orchestrator.confirm(
                sessionId,
                request == null ? null : request.token(),
                request == null ? null : request.idempotencyKey());
        HttpStatus status = "DENIED".equals(response.outcome())
                ? HttpStatus.CONFLICT
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/assistant/sessions/{sessionId}")
    public AssistantResponse view(@PathVariable String sessionId) {
        return orchestrator.view(sessionId);
    }

    /** 用户撤销：只改会话状态，不产生业务事实。 */
    @PostMapping("/assistant/sessions/{sessionId}/cancel")
    public AssistantResponse cancel(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null
                ? null : String.valueOf(body.get("reason"));
        return orchestrator.cancel(sessionId, reason);
    }

    /** 本体输出：概念 / 关系 / 约束 + 硬指标计数，评审第一入口。 */
    @GetMapping("/ontology")
    public Map<String, Object> ontology() {
        Map<String, Object> result = new LinkedHashMap<>(ontology.summary());
        result.put("concepts", ontology.snapshot().concepts());
        result.put("relations", ontology.snapshot().relations());
        result.put("constraints", ontology.snapshot().constraints());
        return result;
    }

    /** 现场改完 JSON 后调用：不重启就把新约束装进去。 */
    @PostMapping("/ontology/reload")
    public Map<String, Object> reloadOntology() {
        ontology.reload();
        Map<String, Object> result = new LinkedHashMap<>(ontology.summary());
        result.put("reloaded", true);
        return result;
    }

    /** 把卡片丢给校验器：非白名单组件、绑定不存在的字段都会在这里被拒。 */
    @PostMapping("/schema/validate")
    public Map<String, Object> validateSchema(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            UiCard card = objectMapper.convertValue(body, UiCard.class);
            SchemaValidator.SchemaValidation validation = schemaValidator.validate(card);
            result.put("valid", validation.valid());
            result.put("errors", validation.errors());
        } catch (IllegalArgumentException e) {
            result.put("valid", false);
            result.put("errors", List.of("卡片结构无法解析（未声明字段一律拒绝）：" + e.getMessage()));
        }
        return result;
    }

    @GetMapping("/events")
    public List<AiEvent> events(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return events.recent(Math.min(Math.max(limit, 1), 200));
        }
        List<AiEvent> bySession = events.bySession(sessionId);
        return bySession.size() > limit ? bySession.subList(0, limit) : bySession;
    }
}
