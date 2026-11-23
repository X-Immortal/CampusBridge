package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * M3 故障开关：{@code none / model_timeout / tool_fail}。
 *
 * <p>开关只影响 demo profile：模型超时走规则降级，工具故障返回 DENIED 且不落库。
 * 单次请求也可以用 {@code context.fault} 覆盖，方便现场演示而不重启。</p>
 */
@Component
public class FaultSwitch {

    public static final String NONE = "none";
    public static final String MODEL_TIMEOUT = "model_timeout";
    public static final String TOOL_FAIL = "tool_fail";

    private final AiProperties properties;

    public FaultSwitch(AiProperties properties) {
        this.properties = properties;
    }

    public String mode() {
        return normalize(properties.getFault() == null ? null : properties.getFault().getMode());
    }

    public String mode(Map<String, Object> context) {
        if (context != null) {
            Object override = context.get("fault");
            if (override != null && !String.valueOf(override).isBlank()) {
                return normalize(String.valueOf(override));
            }
        }
        return mode();
    }

    public boolean modelTimeout(Map<String, Object> context) {
        return MODEL_TIMEOUT.equals(mode(context));
    }

    public boolean modelTimeout() {
        return MODEL_TIMEOUT.equals(mode());
    }

    public boolean toolFail() {
        return TOOL_FAIL.equals(mode());
    }

    private String normalize(String raw) {
        if (raw == null) {
            return NONE;
        }
        String mode = raw.trim().toLowerCase();
        if (MODEL_TIMEOUT.equals(mode) || TOOL_FAIL.equals(mode)) {
            return mode;
        }
        return NONE;
    }
}
