package com.campusbridge.ontology;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 本体里的一条约束。{@code evaluator} 指向 {@code com.campusbridge.policy.ConstraintEvaluators}
 * 里的确定性实现：同一条约束不接受「模型觉得可以」这种答案。
 *
 * <p>{@code phases} 控制这条约束在哪个阶段求值：默认 {@code ["UNDERSTAND", "CONFIRM"]}；
 * 依赖确认令牌或幂等键的约束（K06/K08/K13/K15）只在 {@code CONFIRM} 阶段有意义。</p>
 */
public record OntologyConstraint(
        String id,
        String title,
        String basis,
        Severity severity,
        List<String> phases,
        String evaluator,
        JsonNode params,
        String codeRef,
        String positiveExample,
        String negativeExample) {

    public enum Severity { BLOCK, WARN }

    public OntologyConstraint {
        if (phases == null || phases.isEmpty()) {
            phases = List.of("UNDERSTAND", "CONFIRM");
        }
    }

    public boolean appliesTo(String phase) {
        return phases.contains(phase);
    }
}
