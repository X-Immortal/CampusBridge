package com.campusbridge.gateway.model;

import java.util.Map;

/** 两种实现：真实 LLM（OpenAI 兼容）与规则基线。两者输出同一个结构，才能随时切换。 */
public interface ModelClient {

    ModelUnderstanding extract(String goal, Map<String, Object> context) throws ModelException;

    default String name() {
        return getClass().getSimpleName();
    }
}
