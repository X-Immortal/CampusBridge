package com.campusbridge.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 声明式卡片的唯一载体。模型（或规则基线）只能产出这个结构，
 * 客户端只认白名单组件 —— 这是「不许在线执行模型生成的 ArkTS/JS」的第一道闸。
 *
 * <p>字段全部可空是为了让 JSON 干净；必填项由 {@link SchemaValidator} 检查，而不是靠 Jackson 反序列化成功就算数。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiCard(
        String schemaVersion,
        String cardId,
        String sessionId,
        Long taskId,
        int taskVersion,
        String kind,
        String title,
        List<CardBlock> blocks,
        List<CardAction> actions,
        List<String> warnings,
        String paramDigest,
        TokenView token,
        String generatedBy) {

    public static final String SCHEMA_VERSION = "1.0";

    public UiCard withWarnings(List<String> newWarnings) {
        return new UiCard(schemaVersion, cardId, sessionId, taskId, taskVersion, kind, title,
                blocks, actions, newWarnings, paramDigest, token, generatedBy);
    }
}
