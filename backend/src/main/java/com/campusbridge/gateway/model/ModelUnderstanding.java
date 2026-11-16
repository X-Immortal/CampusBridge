package com.campusbridge.gateway.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型（或规则基线）对一句话的理解结果。它不是事实，只是候选：
 * 槽位最终是否成立由 PolicyEngine 和用户确认决定。
 *
 * @param slots           key 必须是 {@code DraftField} 的 key，非法 key 会被网关丢弃
 * @param declaredFields  用户<b>明说</b>的字段；不在其中的都算系统推测，确认卡上必须标明
 * @param assumptions     这次理解里哪些地方是猜的，必须能展示给用户
 */
public record ModelUnderstanding(
        String intent,
        Map<String, Object> slots,
        Set<String> declaredFields,
        List<String> assumptions,
        double confidence) {
}
