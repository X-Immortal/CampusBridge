package com.campusbridge.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MemoryStore {

    private final Clock clock;
    private final Map<UUID, PersonalMemory> entries = new HashMap<>();

    public MemoryStore() {
        this(Clock.systemUTC());
    }

    MemoryStore(Clock clock) {
        this.clock = clock;
    }

    synchronized PersonalMemory add(long userId, MemoryType type, String value, Instant expiresAt) {
        Instant now = clock.instant();
        purgeExpired(now);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        PersonalMemory memory = new PersonalMemory(UUID.randomUUID(), userId, type, value, expiresAt);
        entries.put(memory.id(), memory);
        return memory;
    }

    /** 同一用户、同一类型、同一句话只留一条；重复确认不会把记忆越堆越多。 */
    synchronized PersonalMemory addIfAbsent(long userId, MemoryType type, String value) {
        purgeExpired(clock.instant());
        return entries.values().stream()
                .filter(memory -> memory.userId() == userId
                        && memory.type() == type
                        && memory.value().equals(value))
                .findFirst()
                .orElseGet(() -> add(userId, type, value, null));
    }

    /** 声明/推测记忆可改值；临时记忆可续期。找不到或不是本人的返回 null。 */
    synchronized PersonalMemory update(long ownerId, UUID id, String value, Instant expiresAt) {
        Instant now = clock.instant();
        purgeExpired(now);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        PersonalMemory current = entries.get(id);
        if (current == null || current.userId() != ownerId) {
            return null;
        }
        PersonalMemory updated = new PersonalMemory(
                current.id(), current.userId(), current.type(), value, expiresAt);
        entries.put(id, updated);
        return updated;
    }

    synchronized List<PersonalMemory> list(long ownerId) {
        purgeExpired(clock.instant());
        return entries.values().stream().filter(memory -> memory.userId() == ownerId).toList();
    }

    synchronized boolean delete(long ownerId, UUID id) {
        purgeExpired(clock.instant());
        PersonalMemory memory = entries.get(id);
        return memory != null && memory.userId() == ownerId && entries.remove(id, memory);
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void purgeExpired() {
        purgeExpired(clock.instant());
    }

    private void purgeExpired(Instant now) {
        entries.values().removeIf(memory -> memory.expiresAt() != null && !memory.expiresAt().isAfter(now));
    }
}
