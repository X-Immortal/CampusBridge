package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import com.campusbridge.events.EventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型网关：模式选择、路由、配额、缓存、成本、超时重试、熔断、降级、事件记录。
 *
 * <p>三档模式（{@code ai.model.mode}）：</p>
 * <ul>
 *   <li>{@code rule} —— 永远走规则基线，评审现场最稳；</li>
 *   <li>{@code llm}  —— 要求走模型，挂了就降级并<b>明确标记 degraded</b>；</li>
 *   <li>{@code auto} —— 配了模型就先用模型，没配就静默走规则。</li>
 * </ul>
 *
 * <p>「降级」不是悄悄换一种结果，而是带着 {@code degraded=true} 和原因返回，事件里也有记录。</p>
 *
 * <p>M3 增补：{@code model.route} / {@code model.cache.hit} / {@code model.cache.miss} /
 * {@code model.quota.exceeded} / {@code model.cost}。缓存只缓存理解结果，不缓存业务动作。</p>
 */
@Component
public class ModelGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGateway.class);
    private static final Set<String> VOLATILE_CONTEXT_KEYS = Set.of(
            "traceId", "requesterId", "modelMode", "model", "fault");

    private final AiProperties properties;
    private final RuleModelClient ruleClient;
    private final OpenAiCompatibleClient llmClient;
    private final EventRecorder events;
    private final ModelRouter router;
    private final ModelQuota quota;
    private final ModelCache cache;
    private final FaultSwitch faultSwitch;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();

    public ModelGateway(
            AiProperties properties,
            RuleModelClient ruleClient,
            OpenAiCompatibleClient llmClient,
            EventRecorder events,
            ModelRouter router,
            ModelQuota quota,
            ModelCache cache,
            FaultSwitch faultSwitch) {
        this.properties = properties;
        this.ruleClient = ruleClient;
        this.llmClient = llmClient;
        this.events = events;
        this.router = router;
        this.quota = quota;
        this.cache = cache;
        this.faultSwitch = faultSwitch;
    }

    public ModelResult understand(String goal, Map<String, Object> context, String traceId, String sessionId) {
        Map<String, Object> ctx = context == null ? Map.of() : context;
        String mode = modeOf(properties.getModel().getMode());
        // 单次请求可以覆盖模式：评测与演示需要现场「把模型关掉」看降级，而不是改配置重启。
        Object override = ctx.get("modelMode");
        if (override == null && "unavailable".equals(ctx.get("model"))) {
            override = "llm";
        }
        if (override != null) {
            mode = modeOf(String.valueOf(override));
        }
        if ("rule".equals(mode)) {
            return new ModelResult(ruleClient.extract(goal, ctx), "rule", false, List.of());
        }

        ModelRoute route = router.route(goal, ctx);
        if (faultSwitch.modelTimeout(ctx)) {
            events.record("model.error", traceId, sessionId, Map.of(
                    "mode", "llm",
                    "attempt", 1,
                    "routeId", route.routeId(),
                    "reason", "fault:model_timeout"));
            events.record("model.fallback", traceId, sessionId, Map.of(
                    "mode", "llm",
                    "routeId", route.routeId(),
                    "reason", "fault:model_timeout"));
            return new ModelResult(ruleClient.extract(goal, ctx), "rule", true,
                    List.of("故障开关 model_timeout：模型调用被强制超时，已退回规则基线"));
        }

        if (!llmClient.configured()) {
            boolean explicit = "llm".equals(mode);
            if (explicit) {
                events.record("model.fallback", traceId, sessionId, Map.of(
                        "mode", "llm", "reason", "not-configured", "routeId", route.routeId()));
            }
            return new ModelResult(ruleClient.extract(goal, ctx), "rule", explicit,
                    explicit ? List.of("模型未配置（baseUrl/apiKey 缺失），已退回规则基线") : List.of());
        }
        if (circuitOpen()) {
            events.record("model.fallback", traceId, sessionId, Map.of(
                    "mode", "llm", "reason", "circuit-open", "routeId", route.routeId()));
            return new ModelResult(ruleClient.extract(goal, ctx), "rule", true,
                    List.of("模型连续失败已熔断，本次改用规则基线"));
        }

        String cacheKey = cacheKey(route, goal, ctx);
        if (cache.enabled()) {
            Optional<ModelUnderstanding> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                recordRoute(route, "hit", traceId, sessionId);
                events.record("model.cache.hit", traceId, sessionId, Map.of(
                        "routeId", route.routeId(),
                        "model", route.model(),
                        "ttlSeconds", properties.getModel().getCacheTtlSeconds()));
                return new ModelResult(cached.get(), "llm", false,
                        List.of("命中模型理解缓存，本次没有重复调用模型"));
            }
            recordRoute(route, "miss", traceId, sessionId);
            events.record("model.cache.miss", traceId, sessionId, Map.of(
                    "routeId", route.routeId(),
                    "model", route.model()));
        } else {
            recordRoute(route, "disabled", traceId, sessionId);
        }

        String requester = requesterOf(ctx);
        if (!quota.tryAcquire(requester)) {
            events.record("model.quota.exceeded", traceId, sessionId, Map.of(
                    "requester", requester,
                    "limit", quota.limit(),
                    "used", quota.usedToday(requester),
                    "routeId", route.routeId(),
                    "model", route.model()));
            return new ModelResult(ruleClient.extract(goal, ctx), "rule", true,
                    List.of("今日模型配额已用尽（" + quota.limit() + " 次），已退回规则基线"));
        }

        long startedAt = System.currentTimeMillis();
        int attempts = Math.max(1, properties.getModel().getMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                // 只给模型客户端补会话标识（OpenCode Go 的 x-opencode-session），
                // 不写回 ctx，避免污染缓存 key。
                Map<String, Object> requestContext = new LinkedHashMap<>(ctx);
                requestContext.put("sessionId", sessionId == null ? "campusbridge" : sessionId);
                ModelUnderstanding understanding = llmClient.extract(goal, requestContext, route.model());
                consecutiveFailures.set(0);
                cache.put(cacheKey, understanding);
                long latencyMs = System.currentTimeMillis() - startedAt;
                ModelCost cost = estimateCost(goal, understanding);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("mode", "llm");
                payload.put("model", route.model());
                payload.put("routeId", route.routeId());
                payload.put("latencyMs", latencyMs);
                payload.put("attempts", attempt);
                payload.put("confidence", understanding.confidence());
                payload.put("degraded", false);
                events.record("model.success", traceId, sessionId, payload);
                events.record("model.cost", traceId, sessionId, cost.payload(route));
                return new ModelResult(understanding, "llm", false, List.of());
            } catch (ModelException ex) {
                LOG.warn("模型调用失败（第 {} 次）：{}", attempt, ex.getMessage());
                events.record("model.error", traceId, sessionId, Map.of(
                        "mode", "llm",
                        "attempt", attempt,
                        "routeId", route.routeId(),
                        "model", route.model(),
                        "reason", String.valueOf(ex.getMessage())));
            }
        }

        quota.release(requester);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.getModel().getCircuitThreshold()) {
            circuitOpenUntil.set(System.currentTimeMillis()
                    + properties.getModel().getCooldownSeconds() * 1000L);
            consecutiveFailures.set(0);
            events.record("model.circuit.open", traceId, sessionId,
                    Map.of("cooldownSeconds", properties.getModel().getCooldownSeconds()));
        }
        events.record("model.fallback", traceId, sessionId, Map.of(
                "mode", "llm",
                "routeId", route.routeId(),
                "reason", "timeout-or-error"));
        return new ModelResult(ruleClient.extract(goal, ctx), "rule", true,
                List.of("模型不可用，已退回规则基线（降级原因见事件 model.fallback）"));
    }

    private void recordRoute(ModelRoute route, String cacheStatus, String traceId, String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routeId", route.routeId());
        payload.put("model", route.model());
        payload.put("reason", route.reason());
        payload.put("cache", cacheStatus);
        events.record("model.route", traceId, sessionId, payload);
    }

    private String cacheKey(ModelRoute route, String goal, Map<String, Object> context) {
        TreeMap<String, Object> stable = new TreeMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!VOLATILE_CONTEXT_KEYS.contains(entry.getKey())) {
                stable.put(entry.getKey(), entry.getValue());
            }
        }
        return route.model() + "|" + (goal == null ? "" : goal.trim()) + "|" + stable;
    }

    private String requesterOf(Map<String, Object> context) {
        Object requester = context.get("requesterId");
        return requester == null ? "anonymous" : String.valueOf(requester);
    }

    private ModelCost estimateCost(String goal, ModelUnderstanding understanding) {
        int inputTokens = 180 + (int) Math.ceil((goal == null ? 0 : goal.length()) / 1.5);
        int outputTokens = 60 + understanding.slots().size() * 14
                + understanding.assumptions().size() * 8;
        double price = properties.getModel().getPricePer1kTokens();
        double estimatedCost = (inputTokens + outputTokens) / 1000.0 * price;
        return new ModelCost(inputTokens, outputTokens, estimatedCost, price);
    }

    private boolean circuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    private String modeOf(String raw) {
        String mode = raw == null ? "auto" : raw.trim().toLowerCase();
        return "rule".equals(mode) || "llm".equals(mode) ? mode : "auto";
    }

    private record ModelCost(int inputTokens, int outputTokens, double estimatedCost,
            double pricePer1kTokens) {
        private Map<String, Object> payload(ModelRoute route) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", route.model());
            payload.put("routeId", route.routeId());
            payload.put("inputTokens", inputTokens);
            payload.put("outputTokens", outputTokens);
            payload.put("totalTokens", inputTokens + outputTokens);
            payload.put("estimatedCost", estimatedCost);
            payload.put("currency", "CNY");
            payload.put("pricePer1kTokens", pricePer1kTokens);
            return payload;
        }
    }
}
