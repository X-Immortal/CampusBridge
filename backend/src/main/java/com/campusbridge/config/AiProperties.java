package com.campusbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 层的全部可调项。默认值刻意做成「没有密钥也能跑」：
 * {@code ai.model.mode=auto} 且没有 baseUrl/apiKey 时自动走规则基线，并把降级原因写进事件。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Model model = new Model();

    private Token token = new Token();

    private Ontology ontology = new Ontology();

    /** M3 故障开关：none / model_timeout / tool_fail。只在 demo profile 使用。 */
    private Fault fault = new Fault();

    /** 事件版本号；没有配置时写 unknown，不阻断业务。 */
    private String promptVersion = "m1-prompt-v1";

    private String schemaVersion = "1.0";

    private String toolVersion = "m1-tools-v1";

    @Data
    public static class Model {
        /** auto / rule / llm */
        private String mode = "auto";
        private String baseUrl;
        private String apiKey;
        private String name = "longcat";
        private int timeoutMs = 4000;
        private int maxRetries = 1;
        private int circuitThreshold = 3;
        private int cooldownSeconds = 30;
        /** 每 1K token 的成本，用于 M3 的成本预算表；M1 只记录不计算。 */
        private double pricePer1kTokens = 0.002;

        /** M3 路由：按 persona / 任务类型选择模型。 */
        private Routing routing = new Routing();

        /** 每个请求者每天最多调用多少次模型；0 表示不限。 */
        private int dailyQuota = 0;

        /** 理解结果缓存 TTL（秒）；0 表示关闭。只缓存理解结果，不缓存业务动作。 */
        private int cacheTtlSeconds = 0;
    }

    @Data
    public static class Routing {
        private boolean enabled = true;
        /** 没有命中规则时使用的模型；为空则回退到 {@code ai.model.name}。 */
        private String defaultModel;
        /** 路由键（PICKUP / MOVING / PURCHASE / SUBSTITUTE / GENERAL）→ 模型名。 */
        private Map<String, String> rules = new LinkedHashMap<>();
        /** persona → 模型名，优先级高于任务类型。 */
        private Map<String, String> personas = new LinkedHashMap<>();
    }

    @Data
    public static class Fault {
        /** none / model_timeout / tool_fail */
        private String mode = "none";
    }

    @Data
    public static class Token {
        /** 不配置则每次启动随机生成，重启后旧令牌全部失效——这在演示里是可接受的。 */
        private String secret;
        private int ttlSeconds = 300;
    }

    @Data
    public static class Ontology {
        private String location = "classpath:ontology/campus-ontology.v1.json";
    }
}
