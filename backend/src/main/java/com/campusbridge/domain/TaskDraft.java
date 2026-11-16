package com.campusbridge.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 一次对话里正在成形的任务。它不是 {@code Task}：只有确认之后才会变成业务事实。
 *
 * <p>每个槽位都记来源（{@link Source}）：来自用户明说的才算声明，模型或规则推出来的一律标 {@code INFERRED}，
 * 推测值不得替代用户确认（约束 K15）。{@code version} 每次被修改就 +1，旧确认令牌因此失效（约束 K13）。</p>
 */
public final class TaskDraft {

    public enum Source { USER, INFERRED, DEFAULT }

    private final Map<String, Object> slots = new LinkedHashMap<>();
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private int version = 1;

    public void put(DraftField field, Object value, Source source) {
        if (value == null) {
            return;
        }
        Object normalized = normalize(field, value);
        if (normalized == null) {
            return;
        }
        slots.put(field.key(), normalized);
        sources.put(field.key(), source);
        version++;
    }

    public void putIfAbsent(DraftField field, Object value, Source source) {
        if (!has(field)) {
            put(field, value, source);
        }
    }

    /** 用户把某一项擦掉：版本同样要升，否则旧确认卡仍然“有效”。 */
    public void remove(DraftField field) {
        if (slots.remove(field.key()) != null) {
            sources.remove(field.key());
            version++;
        }
    }

    /** 用户回答覆盖旧值；即使值相同也升版本，因为「确认过的内容」变了。 */
    public void answer(DraftField field, Object value) {
        put(field, value, Source.USER);
    }

    public boolean has(DraftField field) {
        Object value = slots.get(field.key());
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    public Object raw(DraftField field) {
        return slots.get(field.key());
    }

    public String text(DraftField field) {
        Object value = slots.get(field.key());
        return value == null ? null : String.valueOf(value);
    }

    public Integer integer(DraftField field) {
        Object value = slots.get(field.key());
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public Boolean flag(DraftField field) {
        Object value = slots.get(field.key());
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return null;
    }

    public LocalDateTime dateTime(DraftField field) {
        Object value = slots.get(field.key());
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    public Source sourceOf(DraftField field) {
        return sources.get(field.key());
    }

    public boolean isInferred(DraftField field) {
        return sources.get(field.key()) == Source.INFERRED;
    }

    public String goal() {
        return text(DraftField.GOAL);
    }

    public int version() {
        return version;
    }

    public void bumpVersion() {
        version++;
    }

    public Map<String, Object> slots() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    public Set<String> filledKeys() {
        return Collections.unmodifiableSet(slots.keySet());
    }

    /** 参与「参数摘要」的字段：确认令牌绑定的是这些值的指纹，不是整段对话。 */
    public Map<String, Object> businessSlots() {
        Map<String, Object> copy = new LinkedHashMap<>(slots);
        copy.remove(DraftField.GOAL.key());
        copy.remove(DraftField.INTENT.key());
        copy.remove(DraftField.PERSONA.key());
        return copy;
    }

    private Object normalize(DraftField field, Object value) {
        return switch (field.type()) {
            case DATETIME -> toDateTime(value);
            case NUMBER -> toInteger(value);
            case BOOLEAN -> toBoolean(value);
            default -> String.valueOf(value).trim();
        };
    }

    private Object toDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // 允许只给时间（"21:00"），默认落在今天；日期由规则解析器负责补全。
            try {
                return LocalDateTime.now().toLocalDate().atTime(java.time.LocalTime.parse(text));
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    private Object toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Object toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "是".equals(text)
                || "对".equals(text) || "好".equals(text) || "确认".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "否".equals(text)
                || "不".equals(text) || "取消".equals(text)) {
            return false;
        }
        return null;
    }
}
