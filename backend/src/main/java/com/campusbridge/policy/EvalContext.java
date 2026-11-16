package com.campusbridge.policy;

import com.campusbridge.domain.TaskDraft;
import com.campusbridge.ontology.OntologyConstraint;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** 求值器的入参：草稿 + 约束本身（含参数）+ 阶段上下文。 */
public record EvalContext(TaskDraft draft, OntologyConstraint constraint, PolicyContext policy) {

    public JsonNode params() {
        return constraint.params();
    }

    public String paramText(String name, String defaultValue) {
        JsonNode node = params() == null ? null : params().get(name);
        return node == null || node.isNull() ? defaultValue : node.asText(defaultValue);
    }

    public int paramInt(String name, int defaultValue) {
        JsonNode node = params() == null ? null : params().get(name);
        return node == null || node.isNull() ? defaultValue : node.asInt(defaultValue);
    }

    public List<String> paramStringList(String name) {
        JsonNode node = params() == null ? null : params().get(name);
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }
}
