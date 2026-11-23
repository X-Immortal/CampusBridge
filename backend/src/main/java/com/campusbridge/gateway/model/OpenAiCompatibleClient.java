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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
            6. 所有相对时间（今晚/明天/九点/半小时后）都以用户消息里的“当前时间”为基准换算；
               deadline 必须是未来时间，禁止使用训练数据里的日期。
            7. onlyInAppContact 默认 true；只有用户明确要微信/电话/线下见面时才置 false，
               并把 allowOfflineContact 置 true。
            8. 用户没提到的布尔/枚举字段不要猜，留空让追问 Policy 处理；尤其不要主动把
               onlyInAppContact 设为 false。
            9. completionStandard 必须具体、可验收，至少 10 个汉字；
               例如“送到三号宿舍楼东门并当面交付”，不能只写“取到快递”。
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
        return extract(goal, context, properties.getModel().getName());
    }

    /**
     * M3 路由入口：允许网关按 persona / 任务类型指定模型名；没指定时回退到配置里的默认模型。
     */
    public ModelUnderstanding extract(String goal, Map<String, Object> context, String modelName) {
        if (!configured()) {
            throw new ModelException("模型未配置（baseUrl / apiKey / name 不完整）");
        }
        AiProperties.Model model = properties.getModel();
        String routedModel = modelName == null || modelName.isBlank() ? model.getName() : modelName;
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", routedModel,
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", buildUserContent(goal, context)))));
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(stripTrailingSlash(model.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(model.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + model.getApiKey());
            if (model.getSessionHeader() != null && !model.getSessionHeader().isBlank()) {
                String session = context != null && context.get("sessionId") != null
                        ? String.valueOf(context.get("sessionId"))
                        : "campusbridge";
                builder.header(model.getSessionHeader(), session);
            }
            HttpRequest request = builder
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
        String now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"));
        StringBuilder builder = new StringBuilder("当前时间（Asia/Shanghai）：").append(now)
                .append("\n用户目标：").append(goal);
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
