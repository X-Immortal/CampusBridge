package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import com.campusbridge.events.EventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型网关：模式选择、超时重试、熔断、降级、事件记录。
 *
 * <p>三档模式（{@code ai.model.mode}）：</p>
 * <ul>
 *   <li>{@code rule} —— 永远走规则基线，评审现场最稳；</li>
 *   <li>{@code llm}  —— 要求走模型，挂了就降级并<b>明确标记 degraded</b>；</li>
 *   <li>{@code auto} —— 配了模型就先用模型，没配就静默走规则。</li>
 * </ul>
 *
 * <p>「降级」不是悄悄换一种结果，而是带着 {@code degraded=true} 和原因返回，事件里也有记录。</p>
 */
@Component
public class ModelGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGateway.class);

    private final AiProperties properties;
    private final RuleModelClient ruleClient;
    private final OpenAiCompatibleClient llmClient;
    private final EventRecorder events;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();

    public ModelGateway(
            AiProperties properties,
            RuleModelClient ruleClient,
            OpenAiCompatibleClient llmClient,
            EventRecorder events) {
        this.properties = properties;
        this.ruleClient = ruleClient;
        this.llmClient = llmClient;
        this.events = events;
    }

    public ModelResult understand(String goal, Map<String, Object> context, String traceId, String sessionId) {
        String mode = modeOf(properties.getModel().getMode());
        // 单次请求可以覆盖模式：评测与演示需要现场「把模型关掉」看降级，而不是改配置重启。
        if (context != null) {
            Object override = context.get("modelMode");
            if (override == null && "unavailable".equals(context.get("model"))) {
                override = "llm";
            }
            if (override != null) {
                mode = modeOf(String.valueOf(override));
            }
        }
        if ("rule".equals(mode)) {
            return new ModelResult(ruleClient.extract(goal, context), "rule", false, List.of());
        }
        if (!llmClient.configured()) {
            boolean explicit = "llm".equals(mode);
            if (explicit) {
                events.record("model.fallback", traceId, sessionId,
                        Map.of("mode", "llm", "reason", "not-configured"));
            }
            return new ModelResult(ruleClient.extract(goal, context), "rule", explicit,
                    explicit ? List.of("模型未配置（baseUrl/apiKey 缺失），已退回规则基线") : List.of());
        }
        if (circuitOpen()) {
            events.record("model.fallback", traceId, sessionId,
                    Map.of("mode", "llm", "reason", "circuit-open"));
            return new ModelResult(ruleClient.extract(goal, context), "rule", true,
                    List.of("模型连续失败已熔断，本次改用规则基线"));
        }

        long startedAt = System.currentTimeMillis();
        int attempts = Math.max(1, properties.getModel().getMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ModelUnderstanding understanding = llmClient.extract(goal, context);
                consecutiveFailures.set(0);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("mode", "llm");
                payload.put("model", properties.getModel().getName());
                payload.put("latencyMs", System.currentTimeMillis() - startedAt);
                payload.put("attempts", attempt);
                payload.put("confidence", understanding.confidence());
                events.record("model.success", traceId, sessionId, payload);
                return new ModelResult(understanding, "llm", false, List.of());
            } catch (ModelException ex) {
                LOG.warn("模型调用失败（第 {} 次）：{}", attempt, ex.getMessage());
                events.record("model.error", traceId, sessionId, Map.of(
                        "mode", "llm",
                        "attempt", attempt,
                        "reason", String.valueOf(ex.getMessage())));
            }
        }

        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.getModel().getCircuitThreshold()) {
            circuitOpenUntil.set(System.currentTimeMillis()
                    + properties.getModel().getCooldownSeconds() * 1000L);
            consecutiveFailures.set(0);
            events.record("model.circuit.open", traceId, sessionId,
                    Map.of("cooldownSeconds", properties.getModel().getCooldownSeconds()));
        }
        events.record("model.fallback", traceId, sessionId,
                Map.of("mode", "llm", "reason", "timeout-or-error"));
        return new ModelResult(ruleClient.extract(goal, context), "rule", true,
                List.of("模型不可用，已退回规则基线（降级原因见事件 model.fallback）"));
    }

    private boolean circuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    private String modeOf(String raw) {
        String mode = raw == null ? "auto" : raw.trim().toLowerCase();
        return "rule".equals(mode) || "llm".equals(mode) ? mode : "auto";
    }
}
