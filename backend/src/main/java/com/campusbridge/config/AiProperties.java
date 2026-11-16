package com.campusbridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
