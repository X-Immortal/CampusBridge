package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import com.campusbridge.domain.DraftField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI 兼容的模型客户端（LongCat / Ollama / 任何 /v1/chat/completions 都行）。
 *
 * <p>三个刻意的设计：</p>
 * <ol>
 *   <li>要求模型输出 JSON，但**不信任**输出：槽位 key 不在 {@link DraftField} 里的一律丢弃；</li>
 *   <li>模型说「用户明说了什么」要单独给出 {@code declared}，否则一律按推测处理；</li>
 *   <li>任何异常都抛 {@link ModelException}，由网关统一降级，客户端自己不决定重试。</li>
 * </ol>
 */
@Component
public class OpenAiCompatibleClient implements ModelClient {

    private static final String SYSTEM_PROMPT = """
            你是校园互助平台 CampusHub 的理解层。只输出一个 JSON 对象，不要解释、不要 Markdown。
            结构：{"intent":"...","slots":{...},"declared":["..."],"assumptions":["..."],"confidence":0.0}
            slots 只能使用这些 key：goal,intent,persona,title,category,campus,location,deliveryLocation,
            deadline,timeWindow,rewardType,rewardAmount,currency,rewardDescription,completionStandard,
            requiresProof,onlyInAppContact,allowOfflineContact,riskLevel,riskDisclosureConfirmed,
            shareDormRoom,itemCount,contactPreference
            规则：
            1. 用户没说的地点、时间、报酬一律不要编，留空并在 assumptions 里说明缺什么。
            2. declared 里只放用户原话中明确出现的字段 key；其余都算你的推测。
            3. deadline 用 ISO-8601（yyyy-MM-ddTHH:mm），时区按 Asia/Shanghai。
            4. rewardType 只能是 FREE / POINTS / CASH / NEGOTIABLE。
            5. riskLevel 只能是 LOW / MEDIUM / HIGH；深夜、上门、贵重物品算 HIGH。
            """;

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getModel().getTimeoutMs()))
                .build();
    }

    public boolean configured() {
        AiProperties.Model model = properties.getModel();
        return notBlank(model.getBaseUrl()) && notBlank(model.getApiKey()) && notBlank(model.getName());
    }

    @Override
    public String name() {
        return "openai-compatible:" + properties.getModel().getName();
    }

    @Override
    public ModelUnderstanding extract(String goal, Map<String, Object> context) {
        if (!configured()) {
            throw new ModelException("模型未配置（baseUrl / apiKey / name 不完整）");
        }
        AiProperties.Model model = properties.getModel();
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model.getName(),
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildUserContent(goal, context)))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(stripTrailingSlash(model.getBaseUrl())
                            + "/chat/completions"))
                    .timeout(Duration.ofMillis(model.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ModelException("模型返回 HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ModelException("模型返回空内容");
            }
            return parse(content);
        } catch (IOException e) {
            throw new ModelException("调用模型失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("调用模型被中断", e);
        }
    }

    ModelUnderstanding parse(String content) throws IOException {
        String json = stripFences(content);
        JsonNode node = objectMapper.readTree(json);
        Map<String, Object> slots = new LinkedHashMap<>();
        List<String> assumptions = new ArrayList<>();
        Set<String> declared = new LinkedHashSet<>();
        JsonNode slotNode = node.path("slots");
        if (slotNode.isObject()) {
            slotNode.fieldNames().forEachRemaining(key -> {
                if (DraftField.byKey(key) == null) {
                    assumptions.add("模型给出了未声明的字段 " + key + "，已丢弃");
                    return;
                }
                JsonNode value = slotNode.get(key);
                if (!value.isNull()) {
                    slots.put(key, value.isTextual() ? value.asText() : value.toString());
                }
            });
        }
        JsonNode declaredNode = node.path("declared");
        if (declaredNode.isArray()) {
            declaredNode.forEach(item -> declared.add(item.asText()));
        }
        JsonNode assumptionNode = node.path("assumptions");
        if (assumptionNode.isArray()) {
            assumptionNode.forEach(item -> assumptions.add(item.asText()));
        }
        String intent = node.path("intent").asText("ERRAND");
        return new ModelUnderstanding(intent, slots, declared, assumptions,
                node.path("confidence").asDouble(0.6));
    }

    private String buildUserContent(String goal, Map<String, Object> context) {
        StringBuilder builder = new StringBuilder("用户目标：").append(goal);
        if (context != null && !context.isEmpty()) {
            builder.append("\n上下文：").append(context);
        }
        return builder.toString();
    }

    private String stripFences(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline >= 0 ? text.substring(firstNewline + 1) : text;
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
        }
        return text.trim();
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
