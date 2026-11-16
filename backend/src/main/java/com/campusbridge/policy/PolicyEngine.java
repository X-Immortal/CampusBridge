package com.campusbridge.policy;

import com.campusbridge.domain.TaskDraft;
import com.campusbridge.ontology.OntologyConstraint;
import com.campusbridge.ontology.OntologyRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略引擎：把本体里当前阶段适用的约束逐条跑一遍，收集所有违规。
 *
 * <p>两条设计原则：</p>
 * <ol>
 *   <li><b>全量收集，不短路</b>：一次告诉用户所有问题，而不是修一个再冒一个。</li>
 *   <li><b>不猜</b>：求值器只读草稿和上下文；缺值就是缺值，不允许用默认值蒙混过关。</li>
 * </ol>
 */
@Component
public class PolicyEngine {

    private final OntologyRegistry ontology;
    private final ConstraintEvaluators evaluators;

    public PolicyEngine(OntologyRegistry ontology, ConstraintEvaluators evaluators) {
        this.ontology = ontology;
        this.evaluators = evaluators;
    }

    public PolicyResult evaluate(TaskDraft draft, PolicyContext context) {
        List<PolicyViolation> violations = new ArrayList<>();
        for (OntologyConstraint constraint : ontology.constraintsFor(context.phase())) {
            List<String> problems = evaluators.evaluate(constraint, draft, context);
            for (String problem : problems) {
                violations.add(new PolicyViolation(
                        constraint.id(),
                        constraint.severity().name(),
                        constraint.title(),
                        problem,
                        constraint.codeRef()));
            }
        }
        return new PolicyResult(List.copyOf(violations));
    }
}
