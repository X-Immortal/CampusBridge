package com.campusbridge.policy;

/** 一条被违反的约束。评审时「为什么拒绝」就靠这个对象回答，而不是一句「系统认为不行」。 */
public record PolicyViolation(
        String ruleId,
        String severity,
        String title,
        String message,
        String codeRef) {
}
