package com.campusbridge.domain;

/**
 * 任务草稿里允许出现的字段。
 *
 * <p>这份枚举是「本体 → 卡片 → 校验器」共用的词汇表：
 * UI Schema 里 {@code Field.field} 必须来自这里，否则校验器拒绝渲染（见 {@code SchemaValidator}）；
 * 追问 Policy 也只会问这里定义的字段。</p>
 */
public enum DraftField {
    GOAL("goal", Type.TEXT, "目标原话"),
    INTENT("intent", Type.TEXT, "意图"),
    PERSONA("persona", Type.TEXT, "人物类型"),
    TITLE("title", Type.TEXT, "标题"),
    CATEGORY("category", Type.TEXT, "任务类型"),
    CAMPUS("campus", Type.TEXT, "校区"),
    LOCATION("location", Type.TEXT, "取件地点"),
    DELIVERY_LOCATION("deliveryLocation", Type.TEXT, "送达地点"),
    DEADLINE("deadline", Type.DATETIME, "截止时间"),
    TIME_WINDOW("timeWindow", Type.TEXT, "时间窗描述"),
    REWARD_TYPE("rewardType", Type.ENUM, "报酬形态"),
    REWARD_AMOUNT("rewardAmount", Type.NUMBER, "报酬数量"),
    CURRENCY("currency", Type.TEXT, "币种"),
    REWARD_DESCRIPTION("rewardDescription", Type.TEXT, "报酬说明"),
    COMPLETION_STANDARD("completionStandard", Type.TEXT, "完成标准"),
    REQUIRES_PROOF("requiresProof", Type.BOOLEAN, "是否需要凭证"),
    ONLY_IN_APP_CONTACT("onlyInAppContact", Type.BOOLEAN, "仅站内联络"),
    ALLOW_OFFLINE_CONTACT("allowOfflineContact", Type.BOOLEAN, "允许线下联络"),
    RISK_LEVEL("riskLevel", Type.ENUM, "风险等级"),
    RISK_DISCLOSURE_CONFIRMED("riskDisclosureConfirmed", Type.BOOLEAN, "风险已披露"),
    SHARE_DORM_ROOM("shareDormRoom", Type.BOOLEAN, "共享宿舍号"),
    ITEM_COUNT("itemCount", Type.NUMBER, "取件件数"),
    CONTACT_PREFERENCE("contactPreference", Type.TEXT, "联络偏好");

    public enum Type { TEXT, DATETIME, NUMBER, BOOLEAN, ENUM }

    private final String key;
    private final Type type;
    private final String label;

    DraftField(String key, Type type, String label) {
        this.key = key;
        this.type = type;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public Type type() {
        return type;
    }

    public String label() {
        return label;
    }

    public static DraftField byKey(String key) {
        for (DraftField field : values()) {
            if (field.key.equals(key)) {
                return field;
            }
        }
        return null;
    }
}
