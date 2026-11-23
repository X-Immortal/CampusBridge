package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * M3 模型路由：按 persona / 任务类型选模型。
 *
 * <p>路由只决定「用哪个模型」，不改变理解结果的语义；没有命中规则时回退到默认模型，
 * 路由关闭时同样走默认模型。所有选择原因都会写进 {@code model.route} 事件。</p>
 */
@Component
public class ModelRouter {

    private final AiProperties properties;

    public ModelRouter(AiProperties properties) {
        this.properties = properties;
    }

    public ModelRoute route(String goal, Map<String, Object> context) {
        AiProperties.Model model = properties.getModel();
        AiProperties.Routing routing = model.getRouting();
        String fallback = model.getName();
        if (fallback == null || fallback.isBlank()) {
            fallback = "unknown";
        }
        if (routing == null || !routing.isEnabled()) {
            return new ModelRoute("default", fallback, "routing-disabled");
        }
        if (routing.getDefaultModel() != null && !routing.getDefaultModel().isBlank()) {
            fallback = routing.getDefaultModel();
        }

        if (context != null) {
            String explicit = text(context.get("model"));
            // context.model=unavailable 是故障注入标记，不是模型名，不能拿去路由。
            if (!blank(explicit) && !"unavailable".equalsIgnoreCase(explicit)) {
                return new ModelRoute("context", explicit, "context:model");
            }
            String persona = text(context.get("persona"));
            if (!blank(persona)) {
                String byPersona = findIgnoreCase(routing.getPersonas(), persona);
                if (byPersona != null) {
                    return new ModelRoute(persona, byPersona, "persona:" + persona);
                }
            }
            String taskType = text(context.get("taskType"));
            if (!blank(taskType)) {
                String byTask = findIgnoreCase(routing.getRules(), taskType);
                if (byTask != null) {
                    return new ModelRoute(taskType.toUpperCase(), byTask, "taskType:" + taskType);
                }
            }
        }

        String intent = detectIntent(goal);
        String byIntent = findIgnoreCase(routing.getRules(), intent);
        if (byIntent != null) {
            return new ModelRoute(intent, byIntent, "intent:" + intent);
        }
        return new ModelRoute("default", fallback, "default");
    }

    private String detectIntent(String goal) {
        String text = goal == null ? "" : goal;
        if (text.contains("快递") || text.matches(".*(取|拿|领).{0,4}(件|包裹).*")) {
            return "PICKUP";
        }
        if (text.matches(".*(代课|签到|上课|打卡).*")) {
            return "SUBSTITUTE";
        }
        if (text.matches(".*(搬|运|抬|挪).*")) {
            return "MOVING";
        }
        if (text.matches(".*(买|带饭|外卖|带个|奶茶|咖啡).*")) {
            return "PURCHASE";
        }
        return "GENERAL";
    }

    private String findIgnoreCase(Map<String, String> map, String key) {
        if (map == null || map.isEmpty() || key == null) {
            return null;
        }
        String direct = map.get(key);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
