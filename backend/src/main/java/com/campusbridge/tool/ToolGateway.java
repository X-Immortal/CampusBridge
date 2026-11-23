package com.campusbridge.tool;

import com.campusbridge.domain.TaskDraft;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.gateway.model.FaultSwitch;
import com.campusbridge.policy.PolicyContext;
import com.campusbridge.policy.PolicyEngine;
import com.campusbridge.policy.PolicyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可信工具网关：确认 → 校验 → 幂等 → 落库 → 记录。
 *
 * <p>顺序是有讲究的：<b>先查幂等，再验令牌，再跑策略，最后才落库</b>。
 * 任何一步不通过都不会产生业务事实，而且失败原因会以事件形式留下。</p>
 */
@Component
public class ToolGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ToolGateway.class);
    private static final String ACTION_CREATE_TASK = "createTask";

    private final TokenService tokenService;
    private final PolicyEngine policyEngine;
    private final TaskCommandPort taskCommands;
    private final EventRecorder events;
    private final Clock clock;
    private final FaultSwitch faultSwitch;

    private final Map<String, ToolResult> idempotentResults = new ConcurrentHashMap<>();
    private final Object executionLock = new Object();

    public ToolGateway(
            TokenService tokenService,
            PolicyEngine policyEngine,
            TaskCommandPort taskCommands,
            EventRecorder events,
            Clock clock,
            FaultSwitch faultSwitch) {
        this.tokenService = tokenService;
        this.policyEngine = policyEngine;
        this.taskCommands = taskCommands;
        this.events = events;
        this.clock = clock;
        this.faultSwitch = faultSwitch;
    }

    public ToolResult execute(
            String sessionId,
            Long requesterId,
            TaskDraft draft,
            String tokenValue,
            String idempotencyKey,
            String traceId) {
        synchronized (executionLock) {
            String key = idempotencyKey == null || idempotencyKey.isBlank()
                    ? "token:" + tokenValue
                    : idempotencyKey;
            ToolResult existing = idempotentResults.get(key);
            if (existing != null && existing.ok()) {
                events.record("tool.idempotent.replay", traceId, sessionId, Map.of(
                        "taskId", String.valueOf(existing.taskId()),
                        "idempotencyKey", key));
                return new ToolResult(true, true, existing.taskId(),
                        "这次请求之前已经成功过，没有重复建单", List.of());
            }

            TokenCheck check = tokenService.verify(tokenValue);
            if (!check.ok()) {
                events.record("tool.denied", traceId, sessionId,
                        Map.of("reason", safe(check.reason())));
                return ToolResult.denied(check.reason(), List.of());
            }
            ConfirmToken token = check.token();
            if (!ACTION_CREATE_TASK.equals(token.action())) {
                return ToolResult.denied("这张令牌不是用来建单的（action=" + token.action() + "）", List.of());
            }
            if (!token.sessionId().equals(sessionId)) {
                return ToolResult.denied("令牌不属于当前会话", List.of());
            }

            PolicyContext context = PolicyContext.confirm(
                    LocalDateTime.now(clock), key, true, token.taskVersion(), requesterId);
            PolicyResult policy = policyEngine.evaluate(draft, context);
            if (policy.blocked()) {
                events.record("policy.rejected", traceId, sessionId, Map.of(
                        "rules", policy.violations().stream().map(v -> v.ruleId()).toList(),
                        "messages", policy.messages()));
                return ToolResult.denied("有 " + policy.violations().size() + " 条业务规则没通过，没有建单",
                        policy.violations());
            }

            events.record("tool.request", traceId, sessionId, Map.of(
                    "action", ACTION_CREATE_TASK,
                    "taskVersion", token.taskVersion(),
                    "idempotencyKey", key));
            if (faultSwitch.toolFail()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action", ACTION_CREATE_TASK);
                payload.put("reason", "fault:tool_fail");
                payload.put("idempotencyKey", key);
                events.record("tool.failure", traceId, sessionId, payload);
                return ToolResult.denied("工具故障开关已开启（tool_fail），本次没有落库", List.of());
            }
            try {
                Long taskId = taskCommands.createTask(requesterId, draft);
                tokenService.consume(token.value());
                ToolResult result = ToolResult.success(taskId, false, "已创建任务 #" + taskId);
                idempotentResults.put(key, result);
                events.record("tool.success", traceId, sessionId, Map.of(
                        "taskId", String.valueOf(taskId),
                        "idempotencyKey", key));
                return result;
            } catch (RuntimeException ex) {
                LOG.warn("工具执行失败：{}", ex.getMessage());
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action", ACTION_CREATE_TASK);
                payload.put("error", safe(ex.getMessage()));
                events.record("tool.failure", traceId, sessionId, payload);
                return ToolResult.denied("业务层拒绝了这次建单：" + safe(ex.getMessage()), List.of());
            }
        }
    }

    public int idempotentSize() {
        return idempotentResults.size();
    }

    private static String safe(String value) {
        return value == null ? "未知原因" : value;
    }
}
