package com.campusbridge.policy;

import java.util.List;

/** 确定性求值器：给定同样的草稿和上下文，必须给同样的结果。 */
@FunctionalInterface
public interface ConstraintEvaluator {
    List<String> evaluate(EvalContext context);
}
