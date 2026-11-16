package com.campusbridge.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 卡片内容块。**白名单只有五种**：Text / Field / Choice / Summary / Progress。
 * 不在表里的 {@code type} 会被 {@link SchemaValidator} 整张卡拒绝，而不是只丢掉那一块。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardBlock(
        String type,
        String text,
        String field,
        String label,
        String value,
        String hint,
        Boolean required,
        String name,
        List<ChoiceOption> options,
        String selectedId,
        List<SummaryRow> rows,
        String step,
        String state,
        String detail) {

    public static CardBlock text(String text) {
        return new CardBlock("Text", text, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static CardBlock field(String field, String label, String value, String hint, boolean required) {
        return new CardBlock("Field", null, field, label, value, hint, required,
                null, null, null, null, null, null, null);
    }

    public static CardBlock choice(String name, List<ChoiceOption> options, String selectedId) {
        return new CardBlock("Choice", null, null, null, null, null, null,
                name, options, selectedId, null, null, null, null);
    }

    public static CardBlock summary(List<SummaryRow> rows) {
        return new CardBlock("Summary", null, null, null, null, null, null,
                null, null, null, rows, null, null, null);
    }

    public static CardBlock progress(String step, String state, String detail) {
        return new CardBlock("Progress", null, null, null, null, null, null,
                null, null, null, null, step, state, detail);
    }
}
