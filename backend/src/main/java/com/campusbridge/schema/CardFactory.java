package com.campusbridge.schema;

import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 卡片工厂：把「草稿 + 问题 + 令牌」翻译成声明式卡片。
 *
 * <p>它只产出白名单组件，且每个 Field 的 key 都来自 {@link DraftField}，
 * 所以生成出来的卡一定过得了 {@link SchemaValidator}（有测试守着这条）。</p>
 */
@Component
public class CardFactory {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 确认卡上展示的顺序：先说时间地点，再说报酬，最后说边界。 */
    private static final List<DraftField> SUMMARY_ORDER = List.of(
            DraftField.TITLE,
            DraftField.LOCATION,
            DraftField.DELIVERY_LOCATION,
            DraftField.DEADLINE,
            DraftField.ITEM_COUNT,
            DraftField.REWARD_TYPE,
            DraftField.REWARD_AMOUNT,
            DraftField.REWARD_DESCRIPTION,
            DraftField.COMPLETION_STANDARD,
            DraftField.RISK_LEVEL,
            DraftField.ONLY_IN_APP_CONTACT,
            DraftField.SHARE_DORM_ROOM);

    public UiCard clarification(
            String cardId, String sessionId, int version, List<ClarificationQuestion> questions,
            List<String> warnings, String generatedBy) {
        List<CardBlock> blocks = new ArrayList<>();
        blocks.add(CardBlock.text(questions.size() == 1
                ? "还差一件事就能发布，先确认一下："
                : "还差 " + questions.size() + " 件事就能发布。这几项不补，任务发出去也没人能接："));
        for (ClarificationQuestion question : questions) {
            blocks.add(CardBlock.field(
                    question.field(),
                    question.question(),
                    "",
                    question.reason(),
                    question.blocksAction()));
        }
        return new UiCard(
                UiCard.SCHEMA_VERSION,
                cardId,
                sessionId,
                null,
                version,
                "clarification",
                "先补全这几项",
                blocks,
                List.of(CardAction.primary("answer", "补全并继续"), CardAction.secondary("cancel", "算了")),
                warnings,
                null,
                null,
                generatedBy);
    }

    public UiCard comparison(
            String cardId, String sessionId, int version, TaskDraft draft,
            List<ChoiceOption> options, List<String> warnings, String generatedBy) {
        List<CardBlock> blocks = new ArrayList<>();
        blocks.add(CardBlock.text("你要的东西已经齐了。下面三种方式对时间和确定性影响不一样，选一个："));
        blocks.add(CardBlock.choice("matchMode", options, options.isEmpty() ? null : options.get(0).id()));
        List<SummaryRow> rows = summaryRows(draft);
        if (!rows.isEmpty()) {
            blocks.add(CardBlock.text("我目前理解成这样，选完可以再改："));
            blocks.add(CardBlock.summary(rows));
        }
        return new UiCard(
                UiCard.SCHEMA_VERSION,
                cardId,
                sessionId,
                null,
                version,
                "comparison",
                "怎么匹配更合适",
                blocks,
                List.of(CardAction.primary("select", "就选这个"), CardAction.secondary("cancel", "先不发了")),
                warnings,
                null,
                null,
                generatedBy);
    }

    public UiCard confirmation(
            String cardId, String sessionId, int version, TaskDraft draft, String paramDigest,
            TokenView token, List<String> warnings, String generatedBy) {
        List<CardBlock> blocks = new ArrayList<>();
        blocks.add(CardBlock.text("确认后订单才会真正创建。这一步之前，系统里什么都没有。"));
        blocks.add(CardBlock.summary(summaryRows(draft)));
        if (needsRiskBlock(draft)) {
            blocks.add(CardBlock.text("⚠️ 这个任务在深夜或涉及宿舍/上门，发布后对方会看到风险提示。"));
        }
        return new UiCard(
                UiCard.SCHEMA_VERSION,
                cardId,
                sessionId,
                null,
                version,
                "confirmation",
                "确认一下，点“确认发布”才生效",
                blocks,
                List.of(
                        CardAction.confirm("confirm", "确认发布"),
                        CardAction.secondary("edit", "还要改一处"),
                        CardAction.secondary("cancel", "取消")),
                warnings,
                paramDigest,
                token,
                generatedBy);
    }

    public UiCard progress(
            String cardId, String sessionId, int version, Long taskId, int taskVersion,
            String step, String state, String detail, String generatedBy) {
        String note = taskId == null
                ? "这次没有产生业务事实，卡片可以关掉。"
                : "订单已经落库，任务编号 #" + taskId + "。这张卡片可以关掉，业务事实和授权记录都留在系统里。";
        List<CardBlock> blocks = List.of(
                CardBlock.progress(step, state, detail),
                CardBlock.text(note));
        return new UiCard(
                UiCard.SCHEMA_VERSION,
                cardId,
                sessionId,
                taskId,
                taskVersion,
                "progress",
                "已发布",
                blocks,
                List.of(CardAction.secondary("open", "查看任务")),
                List.of(),
                null,
                null,
                generatedBy);
    }

    public List<SummaryRow> summaryRows(TaskDraft draft) {
        List<SummaryRow> rows = new ArrayList<>();
        for (DraftField field : SUMMARY_ORDER) {
            if (!draft.has(field) || skip(field, draft)) {
                continue;
            }
            rows.add(new SummaryRow(
                    field.label(),
                    display(field, draft),
                    sourceLabel(draft.sourceOf(field))));
        }
        return rows;
    }

    private boolean skip(DraftField field, TaskDraft draft) {
        return field == DraftField.REWARD_AMOUNT && !draft.has(DraftField.REWARD_TYPE);
    }

    private boolean needsRiskBlock(TaskDraft draft) {
        return draft.has(DraftField.RISK_LEVEL)
                || Boolean.TRUE.equals(draft.flag(DraftField.RISK_DISCLOSURE_CONFIRMED));
    }

    public String display(DraftField field, TaskDraft draft) {
        Object value = draft.raw(field);
        if (value == null) {
            return "";
        }
        return switch (field) {
            case DEADLINE -> value instanceof LocalDateTime dateTime ? DATE_TIME.format(dateTime) : String.valueOf(value);
            case REWARD_TYPE -> switch (String.valueOf(value).toUpperCase()) {
                case "CASH" -> "现金";
                case "POINTS" -> "积分";
                case "FREE" -> "免费";
                case "NEGOTIABLE" -> "面议";
                default -> String.valueOf(value);
            };
            case REWARD_AMOUNT -> value + " 元";
            case ITEM_COUNT -> value + " 件";
            case RISK_LEVEL -> switch (String.valueOf(value).toUpperCase()) {
                case "HIGH" -> "高";
                case "MEDIUM" -> "中";
                case "LOW" -> "低";
                default -> String.valueOf(value);
            };
            case REQUIRES_PROOF, ONLY_IN_APP_CONTACT, ALLOW_OFFLINE_CONTACT,
                    SHARE_DORM_ROOM, RISK_DISCLOSURE_CONFIRMED ->
                    Boolean.parseBoolean(String.valueOf(value)) ? "是" : "否";
            default -> String.valueOf(value);
        };
    }

    private String sourceLabel(TaskDraft.Source source) {
        if (source == null) {
            return "系统";
        }
        return switch (source) {
            case USER -> "用户说的";
            case INFERRED -> "系统推测";
            case DEFAULT -> "默认值";
        };
    }

    public String digestOf(TaskDraft draft) {
        // 展示值本身参与摘要，保证「用户看到的」与「工具收到的」是同一份内容（M2 第 8 条测试的基础）。
        StringBuilder builder = new StringBuilder();
        SUMMARY_ORDER.stream().filter(Objects::nonNull).forEach(field -> {
            if (draft.has(field)) {
                builder.append(field.key()).append('=').append(display(field, draft)).append(';');
            }
        });
        return builder.toString();
    }
}
