package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型理解结果缓存（M3）。
 *
 * <p>只缓存 {@link ModelUnderstanding}：同一句话、同一路由、同一组稳定上下文，在 TTL 内
 * 不重复调用模型。业务动作（建单/确认）永远不经过缓存。</p>
 */
@Component
public class ModelCache {

    private final AiProperties properties;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public ModelCache(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.getModel().getCacheTtlSeconds() > 0;
    }

    public Optional<ModelUnderstanding> get(String key) {
        if (!enabled() || key == null) {
            return Optional.empty();
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.understanding());
    }

    public void put(String key, ModelUnderstanding understanding) {
        if (!enabled() || key == null || understanding == null) {
            return;
        }
        Instant expiresAt = clock.instant().plusSeconds(properties.getModel().getCacheTtlSeconds());
        entries.put(key, new Entry(understanding, expiresAt));
    }

    public int size() {
        return entries.size();
    }

    private record Entry(ModelUnderstanding understanding, Instant expiresAt) {
    }
}
