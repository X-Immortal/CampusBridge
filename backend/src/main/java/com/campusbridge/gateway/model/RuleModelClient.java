package com.campusbridge.gateway.model;

import com.campusbridge.domain.DraftField;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则基线：不调用任何模型，只靠确定性的抽取。
 *
 * <p>它的作用不是「比模型聪明」，而是三件事：</p>
 * <ol>
 *   <li>模型不可用时系统还能演、还能建单（降级链里的规则引擎）；</li>
 *   <li>给模型当对照组：同一条目标，规则抽了什么、模型抽了什么，可以直接比；</li>
 *   <li>把「不许猜」写死：时间、地点、报酬没说到就<b>留空</b>，交给追问 Policy 去问；
 *       抽出来了也要标清楚哪些是用户明说的（{@code declaredFields}），哪些是系统补的。</li>
 * </ol>
 */
@Component
public class RuleModelClient implements ModelClient {

    private static final Pattern ITEM_COUNT = Pattern.compile(
            "([一二两三四五六七八九十\\d]{1,3})\\s*(?:个|件|份)?\\s*(?:快递|包裹|东西|外卖|饭)");
    private static final Pattern CLOCK = Pattern.compile(
            "(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*(?:点|时|[:：])\\s*(\\d{1,2}|半)?");
    private static final Pattern CASH = Pattern.compile(
            "(?:预算|报酬|给|赏|付)?\\s*(\\d{1,4})\\s*(?:元|块钱|块)");
    private static final Pattern SPOT = Pattern.compile(
            "(北门|南门|东门|西门|菜鸟驿站|快递站|快递点|快递中心|图书馆|食堂|教学楼|\\d{1,2}\\s*号?(?:宿舍)?楼)");
    private static final Pattern DELIVERY = Pattern.compile(
            "(?:送到|放到|放在|送去)\\s*([^，。；,;]{2,16})");
    private static final Pattern PRIVACY = Pattern.compile(
            "(?:不希望|不想|不要|别)[^，。；]{0,14}(?:宿舍号|宿舍|门牌)");
    private static final Pattern FRIEND_PREFERENCE = Pattern.compile(
            "(?:以前|上次|之前)(?:合作|帮|找)过|熟人|老同学|同一个人|固定的人");
    private static final Pattern OFFLINE_CONTACT = Pattern.compile("微信|vx|VX|QQ|手机号|电话|短信");
    private static final Pattern COFFEE = Pattern.compile("请[^，。；]{0,8}(?:咖啡|奶茶|饮料|吃饭|喝水)");

    private final Clock clock;

    public RuleModelClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ModelUnderstanding extract(String goal, Map<String, Object> context) {
        String text = goal == null ? "" : goal.trim();
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> slots = new LinkedHashMap<>();
        Set<String> declared = new LinkedHashSet<>();
        List<String> assumptions = new ArrayList<>();

        String intent = detectIntent(text);
        slots.put(DraftField.INTENT.key(), intent);
        slots.put(DraftField.CATEGORY.key(),
                "ERRAND_PICKUP".equals(intent) || "PURCHASE".equals(intent) ? "ERRAND" : "AGENCY");
        if (context != null && context.get("persona") != null) {
            slots.put(DraftField.PERSONA.key(), String.valueOf(context.get("persona")));
        }

        Integer count = matchCount(text);
        if (count != null) {
            slots.put(DraftField.ITEM_COUNT.key(), count);
            declared.add(DraftField.ITEM_COUNT.key());
        }

        String location = matchLocation(text, assumptions);
        if (location != null) {
            slots.put(DraftField.LOCATION.key(), location);
            declared.add(DraftField.LOCATION.key());
        }
        Matcher delivery = DELIVERY.matcher(text);
        if (delivery.find()) {
            slots.put(DraftField.DELIVERY_LOCATION.key(), delivery.group(1).trim());
            declared.add(DraftField.DELIVERY_LOCATION.key());
        }

        LocalDateTime deadline = matchDeadline(text, now, assumptions);
        if (deadline != null) {
            slots.put(DraftField.DEADLINE.key(), deadline);
            declared.add(DraftField.DEADLINE.key());
        } else {
            String vague = matchVagueTime(text);
            if (vague != null) {
                slots.put(DraftField.TIME_WINDOW.key(), vague);
                assumptions.add("“" + vague + "”还没精确到点，先只当时时间窗，不替你定截止时间");
            }
        }

        applyReward(text, slots, declared, assumptions);

        if (PRIVACY.matcher(text).find()) {
            slots.put(DraftField.SHARE_DORM_ROOM.key(), false);
            declared.add(DraftField.SHARE_DORM_ROOM.key());
            assumptions.add("你说不希望把宿舍号告诉陌生人，已记成“不共享精确位置”，卡片上只显示楼栋");
        }
        if (FRIEND_PREFERENCE.matcher(text).find()) {
            slots.put(DraftField.CONTACT_PREFERENCE.key(), "FRIEND_FIRST");
            assumptions.add("“最好以前合作过”记成联络偏好：熟人优先，不是硬性条件");
        }
        if (OFFLINE_CONTACT.matcher(text).find()) {
            slots.put(DraftField.ONLY_IN_APP_CONTACT.key(), false);
            slots.put(DraftField.ALLOW_OFFLINE_CONTACT.key(), true);
            assumptions.add("你提到了站外联系方式，已允许线下联络（发布前仍会做敏感信息检查）");
        }

        String risk = riskOf(deadline, text);
        slots.put(DraftField.RISK_LEVEL.key(), risk);

        String title = buildTitle(intent, count, location, text);
        slots.put(DraftField.TITLE.key(), title);
        slots.put(DraftField.COMPLETION_STANDARD.key(), "按要求送达，并由发起人确认后完成");
        slots.put(DraftField.REQUIRES_PROOF.key(), true);
        if (slots.containsKey(DraftField.REWARD_TYPE.key())) {
            slots.put(DraftField.CURRENCY.key(), "CNY");
        }

        double confidence = Math.min(0.9, 0.45 + 0.05 * slots.size());
        assumptions.add("完成标准、凭证要求已用平台默认值，确认卡上标为“系统推测”，你可以直接改");
        return new ModelUnderstanding(intent, slots, declared, assumptions, confidence);
    }

    private String detectIntent(String text) {
        if (text.contains("快递") || text.matches(".*(取|拿|领).{0,4}(件|包裹).*")) {
            return "ERRAND_PICKUP";
        }
        if (text.matches(".*(代课|签到|上课|打卡).*")) {
            return "SUBSTITUTE";
        }
        if (text.matches(".*(搬|运|抬|挪).*")) {
            return "MOVING";
        }
        if (text.matches(".*(买|带饭|外卖|带个).*")) {
            return "PURCHASE";
        }
        return "ERRAND";
    }

    private Integer matchCount(String text) {
        Matcher matcher = ITEM_COUNT.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return toInt(matcher.group(1));
    }

    private String matchLocation(String text, List<String> assumptions) {
        Matcher matcher = SPOT.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            values.add(matcher.group(1).replaceAll("\\s+", ""));
        }
        if (values.isEmpty()) {
            return null;
        }
        for (int i = 0; i < values.size(); i++) {
            int start = starts.get(i);
            String before = start >= 2 ? text.substring(Math.max(0, start - 3), start) : "";
            if (before.contains("从") || before.contains("在") || before.contains("去") || before.contains("到")) {
                return values.get(i);
            }
        }
        if (values.size() > 1) {
            assumptions.add("句子里出现了多个地点（" + String.join("、", values) + "），先取第一个当取件点");
        }
        return values.get(0);
    }

    private LocalDateTime matchDeadline(String text, LocalDateTime now, List<String> assumptions) {
        Matcher matcher = CLOCK.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        Integer hour = toInt(matcher.group(1));
        if (hour == null) {
            return null;
        }
        int minute = 0;
        String minuteGroup = matcher.group(2);
        if ("半".equals(minuteGroup)) {
            minute = 30;
        } else if (minuteGroup != null) {
            minute = Integer.parseInt(minuteGroup);
        }
        if (hour > 23 || minute > 59) {
            return null;
        }
        boolean evening = text.contains("晚上") || text.contains("今晚") || text.contains("夜里")
                || text.contains("傍晚") || text.contains("下课");
        boolean afternoon = text.contains("下午") || text.contains("中午");
        if (evening && hour < 12) {
            hour += 12;
            assumptions.add("把“" + matcher.group(1) + "点”按晚上理解为 " + hour + ":"
                    + String.format("%02d", minute));
        } else if (afternoon && hour < 12) {
            hour += 12;
            assumptions.add("把“" + matcher.group(1) + "点”按下午理解为 " + hour + ":"
                    + String.format("%02d", minute));
        }
        LocalDateTime candidate = now.toLocalDate().atTime(LocalTime.of(hour, minute));
        if (text.contains("明天")) {
            return candidate.plusDays(1);
        }
        if (!candidate.isAfter(now)) {
            assumptions.add("这个时间今天已经过了，按明天同一个时间理解");
            return candidate.plusDays(1);
        }
        return candidate;
    }

    private String matchVagueTime(String text) {
        if (text.contains("今晚") || text.contains("晚上")) {
            return "今晚";
        }
        if (text.contains("明天")) {
            return "明天";
        }
        if (text.contains("今天")) {
            return "今天";
        }
        Matcher minutes = Pattern.compile("(\\d{1,3})\\s*分钟").matcher(text);
        if (minutes.find()) {
            return minutes.group(1) + " 分钟内";
        }
        return null;
    }

    private void applyReward(String text, Map<String, Object> slots, Set<String> declared,
            List<String> assumptions) {
        Matcher cash = CASH.matcher(text);
        if (cash.find()) {
            slots.put(DraftField.REWARD_TYPE.key(), "CASH");
            slots.put(DraftField.REWARD_AMOUNT.key(), Integer.parseInt(cash.group(1)));
            slots.put(DraftField.CURRENCY.key(), "CNY");
            declared.add(DraftField.REWARD_TYPE.key());
            declared.add(DraftField.REWARD_AMOUNT.key());
            return;
        }
        Matcher coffee = COFFEE.matcher(text);
        if (coffee.find()) {
            slots.put(DraftField.REWARD_TYPE.key(), "NEGOTIABLE");
            slots.put(DraftField.REWARD_DESCRIPTION.key(), coffee.group().trim());
            declared.add(DraftField.REWARD_TYPE.key());
            declared.add(DraftField.REWARD_DESCRIPTION.key());
            assumptions.add("把“" + coffee.group().trim() + "”记成面议报酬，没有折成金额");
            return;
        }
        if (text.contains("免费") || text.contains("无偿") || text.contains("不要钱")) {
            slots.put(DraftField.REWARD_TYPE.key(), "FREE");
            declared.add(DraftField.REWARD_TYPE.key());
            return;
        }
        if (text.contains("积分")) {
            slots.put(DraftField.REWARD_TYPE.key(), "POINTS");
            declared.add(DraftField.REWARD_TYPE.key());
            Matcher points = Pattern.compile("(\\d{1,4})\\s*积分").matcher(text);
            if (points.find()) {
                slots.put(DraftField.REWARD_AMOUNT.key(), Integer.parseInt(points.group(1)));
                declared.add(DraftField.REWARD_AMOUNT.key());
            }
        }
    }

    private String riskOf(LocalDateTime deadline, String text) {
        if (deadline != null) {
            int hour = deadline.getHour();
            if (hour >= 22 || hour < 6) {
                return "HIGH";
            }
        }
        if (text.contains("宿舍") || text.contains("上门") || text.contains("家里")) {
            return "HIGH";
        }
        if (text.matches(".*(电脑|手机|贵重|证件|钱包).*")) {
            return "HIGH";
        }
        return "LOW";
    }

    private String buildTitle(String intent, Integer count, String location, String goal) {
        String suffix = location == null ? "" : "（" + location + "）";
        String title = switch (intent) {
            case "ERRAND_PICKUP" -> "帮取" + (count == null ? "快递" : count + "个快递") + suffix;
            case "MOVING" -> "帮忙搬东西" + suffix;
            case "PURCHASE" -> "帮忙带一份" + suffix;
            case "SUBSTITUTE" -> "帮忙代一节课";
            default -> goal.length() > 30 ? goal.substring(0, 30) : goal;
        };
        if (title.codePointCount(0, title.length()) < 5) {
            return goal.length() > 40 ? goal.substring(0, 40) : goal;
        }
        return title;
    }

    private Integer toInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.matches("\\d+")) {
            return Integer.parseInt(raw);
        }
        Map<Character, Integer> digits = Map.of(
                '一', 1, '二', 2, '两', 2, '三', 3, '四', 4, '五', 5,
                '六', 6, '七', 7, '八', 8, '九', 9);
        if (raw.length() == 1) {
            return raw.charAt(0) == '十' ? Integer.valueOf(10) : digits.get(raw.charAt(0));
        }
        if (raw.length() == 2 && raw.charAt(1) == '十') {
            Integer tens = digits.get(raw.charAt(0));
            return tens == null ? null : tens * 10;
        }
        if (raw.length() == 2 && raw.charAt(0) == '十') {
            Integer ones = digits.get(raw.charAt(1));
            return ones == null ? null : 10 + ones;
        }
        if (raw.length() == 3 && raw.charAt(1) == '十') {
            Integer tens = digits.get(raw.charAt(0));
            Integer ones = digits.get(raw.charAt(2));
            return tens == null || ones == null ? null : tens * 10 + ones;
        }
        return null;
    }
}
