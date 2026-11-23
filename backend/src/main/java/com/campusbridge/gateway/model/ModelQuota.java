package com.campusbridge.gateway.model;

import com.campusbridge.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日模型调用配额（M3）。按「自然日 + 请求者」计数；0 表示不限。
 *
 * <p>只在真正发起模型调用前占用，调用彻底失败会归还，避免一次网络故障吃掉全天额度。</p>
 */
@Component
public class ModelQuota {

    private final AiProperties properties;
    private final Clock clock;
    private final Map<String, Integer> used = new ConcurrentHashMap<>();

    public ModelQuota(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(String requester) {
        int limit = properties.getModel().getDailyQuota();
        if (limit <= 0) {
            return true;
        }
        String key = key(requester);
        synchronized (used) {
            int current = used.getOrDefault(key, 0);
            if (current >= limit) {
                return false;
            }
            used.put(key, current + 1);
            return true;
        }
    }

    public void release(String requester) {
        if (properties.getModel().getDailyQuota() <= 0) {
            return;
        }
        String key = key(requester);
        synchronized (used) {
            int current = used.getOrDefault(key, 0);
            if (current > 0) {
                used.put(key, current - 1);
            }
        }
    }

    public int usedToday(String requester) {
        return used.getOrDefault(key(requester), 0);
    }

    public int limit() {
        return properties.getModel().getDailyQuota();
    }

    private String key(String requester) {
        return LocalDate.now(clock) + "|" + (requester == null || requester.isBlank() ? "anonymous" : requester);
    }
}
