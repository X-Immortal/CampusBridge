package com.campusbridge.schema;

import com.campusbridge.domain.DraftField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * UI Schema 校验器。三条硬规则（课件 03 p71 / 05 p75）：
 * <ol>
 *   <li>组件名不在白名单 → 整张卡拒绝（不是静默丢掉那一块）；</li>
 *   <li>{@code Field.field} 不在当前任务模型里 → 拒绝（防止模型编字段）；</li>
 *   <li>绑定高风险动作的按钮没有 {@code requiresConfirmation} → 拒绝。</li>
 * </ol>
 *
 * <p>另外两条自己加的：确认动作必须带令牌；令牌的参数摘要必须能和卡片摘要对上
 * （为 M2 的「UI 展示值与工具参数摘要一致」打底）。</p>
 */
@Component
public class SchemaValidator {

    public static final Set<String> ALLOWED_BLOCKS = Set.of("Text", "Field", "Choice", "Summary", "Progress");
    public static final Set<String> ALLOWED_ACTIONS = Set.of("answer", "select", "confirm", "edit", "cancel", "retry", "open");
    public static final Set<String> CARD_KINDS = Set.of("clarification", "comparison", "confirmation", "progress");
    public static final Set<String> PROGRESS_STATES = Set.of("done", "active", "pending", "failed");

    private static final Set<String> SIDE_EFFECT_ACTIONS = Set.of("confirm");

    public SchemaValidation validate(UiCard card) {
        List<String> errors = new ArrayList<>();
        if (card == null) {
            return new SchemaValidation(List.of("卡片为空"));
        }
        if (!UiCard.SCHEMA_VERSION.equals(card.schemaVersion())) {
            errors.add("不支持的 schemaVersion：" + card.schemaVersion());
        }
        if (isBlank(card.cardId())) {
            errors.add("缺少 cardId");
        }
        if (card.kind() == null || !CARD_KINDS.contains(card.kind())) {
            errors.add("未知卡片类型：" + card.kind());
        }
        validateBlocks(card, errors);
        validateActions(card, errors);
        validateToken(card, errors);
        return new SchemaValidation(List.copyOf(errors));
    }

    private void validateBlocks(UiCard card, List<String> errors) {
        if (card.blocks() == null || card.blocks().isEmpty()) {
            errors.add("卡片没有任何内容块");
            return;
        }
        boolean hasField = false;
        for (CardBlock block : card.blocks()) {
            if (block == null || isBlank(block.type())) {
                errors.add("存在没有 type 的内容块");
                continue;
            }
            if (!ALLOWED_BLOCKS.contains(block.type())) {
                errors.add("非白名单组件被拒绝：" + block.type());
                continue;
            }
            switch (block.type()) {
                case "Text" -> {
                    if (isBlank(block.text())) {
                        errors.add("Text 块缺少 text");
                    }
                }
                case "Field" -> {
                    hasField = true;
                    if (isBlank(block.field()) || DraftField.byKey(block.field()) == null) {
                        errors.add("Field 绑定了不存在的字段：" + block.field());
                    }
                    if (isBlank(block.label())) {
                        errors.add("Field " + block.field() + " 缺少 label");
                    }
                }
                case "Choice" -> {
                    if (block.options() == null || block.options().isEmpty()) {
                        errors.add("Choice 块没有选项");
                    } else {
                        for (ChoiceOption option : block.options()) {
                            if (isBlank(option.id()) || isBlank(option.label())) {
                                errors.add("Choice 选项缺少 id 或 label");
                            }
                        }
                    }
                }
                case "Summary" -> {
                    if (block.rows() == null || block.rows().isEmpty()) {
                        errors.add("Summary 块没有内容行");
                    }
                }
                case "Progress" -> {
                    if (isBlank(block.step())) {
                        errors.add("Progress 块缺少 step");
                    }
                    if (isBlank(block.state()) || !PROGRESS_STATES.contains(block.state())) {
                        errors.add("Progress 状态非法：" + block.state());
                    }
                }
                default -> errors.add("非白名单组件被拒绝：" + block.type());
            }
        }
        if (hasField && (card.actions() == null
                || card.actions().stream().noneMatch(a -> "answer".equals(a.actionId())))) {
            errors.add("有可编辑字段却没有 answer 动作");
        }
    }

    private void validateActions(UiCard card, List<String> errors) {
        List<CardAction> actions = card.actions();
        if (actions == null) {
            if (!"progress".equals(card.kind())) {
                errors.add("卡片没有任何动作");
            }
            return;
        }
        if (actions.isEmpty() && !"progress".equals(card.kind())) {
            errors.add("卡片没有任何动作");
        }
        boolean hasSideEffect = false;
        for (CardAction action : actions) {
            if (action == null || isBlank(action.actionId())) {
                errors.add("存在没有 actionId 的按钮");
                continue;
            }
            if (!ALLOWED_ACTIONS.contains(action.actionId())) {
                errors.add("未声明的动作：" + action.actionId());
            }
            if (isBlank(action.label())) {
                errors.add("动作 " + action.actionId() + " 缺少文案");
            }
            if (SIDE_EFFECT_ACTIONS.contains(action.actionId())) {
                hasSideEffect = true;
                if (!action.requiresConfirmation()) {
                    errors.add("高风险动作 " + action.actionId() + " 没有要求确认");
                }
            }
        }
        if (hasSideEffect && card.token() == null) {
            errors.add("确认动作没有令牌");
        }
    }

    private void validateToken(UiCard card, List<String> errors) {
        if (card.token() == null) {
            return;
        }
        if (isBlank(card.token().value()) || isBlank(card.token().nonce())) {
            errors.add("令牌缺少 value 或 nonce");
        }
        if (isBlank(card.token().paramDigest())) {
            errors.add("令牌缺少参数摘要");
        }
        if (!isBlank(card.paramDigest()) && !card.paramDigest().equals(card.token().paramDigest())) {
            errors.add("卡片展示的参数与令牌摘要不一致");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 校验结果：错误列表为空才算通过。 */
    public record SchemaValidation(List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
