package com.campusbridge.policy;

import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.ontology.OntologyConstraint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 所有确定性约束的唯一实现处。本体 JSON 里的 {@code evaluator} 字段必须在这里找得到，
 * 找不到就启动失败（见 {@link com.campusbridge.ontology.OntologyRegistry} 的校验）。
 *
 * <p>规矩：一个求值器只回答「违反了没有」和「为什么」，不做数据库访问、不调用模型、不看时钟以外的东西。</p>
 */
@Component
public class ConstraintEvaluators {

    private final Map<String, ConstraintEvaluator> registry;

    public ConstraintEvaluators() {
        Map<String, ConstraintEvaluator> map = new LinkedHashMap<>();
        map.put("reward.amount.positive", this::rewardAmountPositive);
        map.put("currency.allowed", this::currencyAllowed);
        map.put("clarification.required", this::clarificationRequired);
        map.put("deadline.future", this::deadlineFuture);
        map.put("risk.disclosure", this::riskDisclosure);
        map.put("confirmation.required", this::confirmationRequired);
        map.put("privacy.dorm", this::privacyDorm);
        map.put("idempotency.unique", this::idempotencyUnique);
        map.put("title.length", this::titleLength);
        map.put("completion.standard", this::completionStandard);
        map.put("contact.policy", this::contactPolicy);
        map.put("reward.type.allowed", this::rewardTypeAllowed);
        map.put("version.increment", this::versionIncrement);
        map.put("items.max", this::itemsMax);
        map.put("preference.inferred.confirmation", this::inferredPreferenceConfirmation);
        this.registry = Map.copyOf(map);
    }

    public Set<String> names() {
        return registry.keySet();
    }

    public List<String> evaluate(OntologyConstraint constraint, TaskDraft draft, PolicyContext policy) {
        ConstraintEvaluator evaluator = registry.get(constraint.evaluator());
        if (evaluator == null) {
            return List.of("约束 " + constraint.id() + " 的求值器未实现：" + constraint.evaluator());
        }
        return evaluator.evaluate(new EvalContext(draft, constraint, policy));
    }

    // ---- K01 -----------------------------------------------------------------
    private List<String> rewardAmountPositive(EvalContext ctx) {
        TaskDraft draft = ctx.draft();
        String type = upper(draft.text(DraftField.REWARD_TYPE));
        Integer amount = draft.integer(DraftField.REWARD_AMOUNT);
        boolean allowFree = ctx.params() == null || ctx.params().path("allowFree").asBoolean(true);

        if (amount != null && amount <= 0) {
            return List.of("报酬数量必须大于 0（当前 " + amount + "，本题的约束写死在这里，不接受负数）");
        }
        if (type == null) {
            return List.of();
        }
        if ("FREE".equals(type) && !allowFree) {
            return List.of("本环境不允许免费任务");
        }
        if (("CASH".equals(type) || "POINTS".equals(type)) && amount == null) {
            return List.of("报酬形态是 " + type + "，必须给出具体数量");
        }
        if ("NEGOTIABLE".equals(type) && isBlank(draft.text(DraftField.REWARD_DESCRIPTION))) {
            return List.of("报酬形态是 NEGOTIABLE，必须写清用什么答谢（例如“请喝一杯咖啡”）");
        }
        return List.of();
    }

    // ---- K02 -----------------------------------------------------------------
    private List<String> currencyAllowed(EvalContext ctx) {
        String currency = upper(ctx.draft().text(DraftField.CURRENCY));
        if (currency == null || currency.isBlank()) {
            return List.of();
        }
        List<String> allowed = ctx.paramStringList("allowed");
        if (!allowed.isEmpty() && !allowed.contains(currency)) {
            return List.of("货币只能是 " + String.join(" / ", allowed) + "（当前 " + currency + "）");
        }
        return List.of();
    }

    // ---- K03 -----------------------------------------------------------------
    private List<String> clarificationRequired(EvalContext ctx) {
        List<String> missing = new ArrayList<>();
        for (String key : ctx.paramStringList("fields")) {
            DraftField field = DraftField.byKey(key);
            if (field == null || !ctx.draft().has(field)) {
                missing.add(field == null ? key : field.label());
            }
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        return List.of("还缺 " + String.join("、", missing) + "，只能继续澄清，不能进入确认");
    }

    // ---- K04 -----------------------------------------------------------------
    private List<String> deadlineFuture(EvalContext ctx) {
        LocalDateTime deadline = ctx.draft().dateTime(DraftField.DEADLINE);
        if (deadline == null) {
            return List.of();
        }
        if (!deadline.isAfter(ctx.policy().now())) {
            return List.of("截止时间必须晚于现在（当前是 " + deadline + "）");
        }
        return List.of();
    }

    // ---- K05 -----------------------------------------------------------------
    private List<String> riskDisclosure(EvalContext ctx) {
        TaskDraft draft = ctx.draft();
        List<String> reasons = new ArrayList<>();
        if ("HIGH".equals(upper(draft.text(DraftField.RISK_LEVEL)))) {
            reasons.add("被标为高风险");
        }
        if (isLate(draft.dateTime(DraftField.DEADLINE),
                ctx.paramText("lateFrom", "22:00"), ctx.paramText("lateTo", "06:00"))) {
            reasons.add("时间落在深夜");
        }
        if (containsAny(draft.text(DraftField.LOCATION) + " " + draft.text(DraftField.DELIVERY_LOCATION),
                "宿舍", "寝室", "上门")) {
            reasons.add("涉及宿舍/上门");
        }
        if (reasons.isEmpty()) {
            return List.of();
        }
        if (Boolean.TRUE.equals(draft.flag(DraftField.RISK_DISCLOSURE_CONFIRMED))) {
            return List.of();
        }
        return List.of("这次任务" + String.join("、", reasons) + "，必须先向用户披露风险并取得确认");
    }

    // ---- K06 -----------------------------------------------------------------
    private List<String> confirmationRequired(EvalContext ctx) {
        if (ctx.policy().confirmationPresent()) {
            return List.of();
        }
        return List.of("该动作会产生真实业务事实，必须由用户显式确认，推测不能代替");
    }

    // ---- K07 -----------------------------------------------------------------
    private List<String> privacyDorm(EvalContext ctx) {
        String location = ctx.draft().text(DraftField.LOCATION);
        if (!hasPreciseRoom(location)) {
            return List.of();
        }
        if (Boolean.TRUE.equals(ctx.draft().flag(DraftField.SHARE_DORM_ROOM))) {
            return List.of();
        }
        return List.of("地点里出现了具体宿舍号（" + location + "），但用户没有同意共享精确位置");
    }

    // ---- K08 -----------------------------------------------------------------
    private List<String> idempotencyUnique(EvalContext ctx) {
        if (isBlank(ctx.policy().idempotencyKey())) {
            return List.of("缺少幂等键：网络重试和重复点击必须能识别成同一次请求");
        }
        // 真正的「只产生一次事实」在 ToolGateway 的幂等表里完成，这里只保证请求带上键。
        return List.of();
    }

    // ---- K09 -----------------------------------------------------------------
    private List<String> titleLength(EvalContext ctx) {
        String title = ctx.draft().text(DraftField.TITLE);
        if (isBlank(title)) {
            return List.of();
        }
        int length = codePoints(title);
        int min = ctx.paramInt("min", 5);
        int max = ctx.paramInt("max", 100);
        if (length < min || length > max) {
            return List.of("标题要 " + min + "–" + max + " 字（当前 " + length + " 字）");
        }
        return List.of();
    }

    // ---- K10 -----------------------------------------------------------------
    private List<String> completionStandard(EvalContext ctx) {
        String standard = ctx.draft().text(DraftField.COMPLETION_STANDARD);
        int min = ctx.paramInt("minLength", 10);
        if (isBlank(standard)) {
            return List.of("必须写清完成标准：怎样算完成、由谁验收");
        }
        int length = codePoints(standard);
        if (length < min) {
            return List.of("完成标准太短（" + length + " 字），至少 " + min + " 字才算可验收");
        }
        return List.of();
    }

    // ---- K11 -----------------------------------------------------------------
    private List<String> contactPolicy(EvalContext ctx) {
        Boolean onlyInApp = ctx.draft().flag(DraftField.ONLY_IN_APP_CONTACT);
        Boolean offline = ctx.draft().flag(DraftField.ALLOW_OFFLINE_CONTACT);
        if (Boolean.FALSE.equals(onlyInApp) && !Boolean.TRUE.equals(offline)) {
            return List.of("关掉了站内联络，却又没打开线下联络，双方根本无法碰面");
        }
        return List.of();
    }

    // ---- K12 -----------------------------------------------------------------
    private List<String> rewardTypeAllowed(EvalContext ctx) {
        String type = upper(ctx.draft().text(DraftField.REWARD_TYPE));
        if (type == null || type.isBlank()) {
            return List.of();
        }
        List<String> allowed = ctx.paramStringList("allowed");
        if (!allowed.isEmpty() && !allowed.contains(type)) {
            return List.of("报酬形态必须是 " + String.join(" / ", allowed) + "（当前 " + type + "）");
        }
        return List.of();
    }

    // ---- K13 -----------------------------------------------------------------
    private List<String> versionIncrement(EvalContext ctx) {
        Integer tokenVersion = ctx.policy().tokenVersion();
        if (tokenVersion == null) {
            return List.of();
        }
        if (tokenVersion != ctx.draft().version()) {
            return List.of("确认卡已过期：令牌绑定版本 " + tokenVersion
                    + "，任务已经改到版本 " + ctx.draft().version());
        }
        return List.of();
    }

    // ---- K14 -----------------------------------------------------------------
    private List<String> itemsMax(EvalContext ctx) {
        Integer count = ctx.draft().integer(DraftField.ITEM_COUNT);
        int max = ctx.paramInt("max", 5);
        if (count != null && count > max) {
            return List.of("单次最多 " + max + " 件（当前 " + count + " 件），请拆成多个任务");
        }
        return List.of();
    }

    // ---- K15 -----------------------------------------------------------------
    private List<String> inferredPreferenceConfirmation(EvalContext ctx) {
        TaskDraft draft = ctx.draft();
        boolean risky = "HIGH".equals(upper(draft.text(DraftField.RISK_LEVEL)));
        boolean inferredPrivacy = draft.isInferred(DraftField.SHARE_DORM_ROOM)
                || draft.isInferred(DraftField.LOCATION);
        if (!risky && !inferredPrivacy) {
            return List.of();
        }
        if (ctx.policy().confirmationPresent()) {
            return List.of();
        }
        return List.of("有系统推测的值参与了这个高风险动作，必须让用户确认，不能替他决定");
    }

    // ---- helpers -------------------------------------------------------------

    private static boolean hasPreciseRoom(String location) {
        if (isBlank(location)) {
            return false;
        }
        // 「5 号楼 502」「502 室」「3 号宿舍楼 4-201」算精确位置；「5 号楼附近」不算。
        return location.matches(".*(?:(?:楼|宿舍|寝室)\\s*-?\\d{2,4}|\\d{2,4}\\s*(?:室|寝)).*");
    }

    private static boolean isLate(LocalDateTime time, String from, String to) {
        if (time == null) {
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(from);
            LocalTime end = LocalTime.parse(to);
            LocalTime actual = time.toLocalTime();
            if (!start.isAfter(end)) {
                return !actual.isBefore(start) && actual.isBefore(end);
            }
            return !actual.isBefore(start) || actual.isBefore(end);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean containsAny(String text, String... keys) {
        if (isBlank(text)) {
            return false;
        }
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String upper(String text) {
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
