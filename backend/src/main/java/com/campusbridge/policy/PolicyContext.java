package com.campusbridge.policy;

import java.time.LocalDateTime;

/**
 * 求值上下文：约束不仅看草稿，还要看「现在几点」「有没有确认」「令牌绑的哪个版本」。
 *
 * <p>{@code phase} 只有两个值：{@code UNDERSTAND}（还在澄清/比较，只做提示性检查）与
 * {@code CONFIRM}（要产生副作用了，逐条硬校验）。</p>
 */
public record PolicyContext(
        String phase,
        LocalDateTime now,
        String idempotencyKey,
        boolean confirmationPresent,
        Integer tokenVersion,
        Long requesterId) {

    public static final String UNDERSTAND = "UNDERSTAND";
    public static final String CONFIRM = "CONFIRM";

    public static PolicyContext understand(LocalDateTime now) {
        return new PolicyContext(UNDERSTAND, now, null, false, null, null);
    }

    public static PolicyContext confirm(
            LocalDateTime now, String idempotencyKey, boolean confirmationPresent,
            Integer tokenVersion, Long requesterId) {
        return new PolicyContext(CONFIRM, now, idempotencyKey, confirmationPresent, tokenVersion, requesterId);
    }
}
