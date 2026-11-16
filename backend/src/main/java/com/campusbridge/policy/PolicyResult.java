package com.campusbridge.policy;

import java.util.List;

/** 一次策略求值的结果：全部违规 + 是否被硬拦。 */
public record PolicyResult(List<PolicyViolation> violations) {

    public boolean blocked() {
        return violations.stream().anyMatch(v -> "BLOCK".equals(v.severity()));
    }

    public boolean clean() {
        return violations.isEmpty();
    }

    public List<String> messages() {
        return violations.stream().map(v -> v.ruleId() + "：" + v.message()).toList();
    }

    public String firstMessage() {
        return violations.isEmpty() ? null : violations.get(0).message();
    }
}
