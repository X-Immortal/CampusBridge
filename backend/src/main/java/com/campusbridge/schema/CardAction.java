package com.campusbridge.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 卡片上的按钮。{@code requiresConfirmation} 为真的动作，在确认卡之外出现一律视为非法。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardAction(String actionId, String label, String kind, boolean requiresConfirmation) {

    public static CardAction primary(String actionId, String label) {
        return new CardAction(actionId, label, "primary", false);
    }

    public static CardAction confirm(String actionId, String label) {
        return new CardAction(actionId, label, "primary", true);
    }

    public static CardAction secondary(String actionId, String label) {
        return new CardAction(actionId, label, "secondary", false);
    }
}
