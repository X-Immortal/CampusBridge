package com.campusbridge.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Callers supply the owner ID from the authenticated principal. */
@Service
public class MemoryService {

    private final MemoryStore store;

    public MemoryService(MemoryStore store) {
        this.store = store;
    }

    public PersonalMemory add(long ownerId, MemoryType type, String value, Instant expiresAt) {
        return store.add(ownerId, type, value, expiresAt);
    }

    public PersonalMemory addIfAbsent(long ownerId, MemoryType type, String value) {
        return store.addIfAbsent(ownerId, type, value);
    }

    public List<PersonalMemory> list(long ownerId) {
        return store.list(ownerId);
    }

    public PersonalMemory update(long ownerId, UUID id, String value, Instant expiresAt) {
        return store.update(ownerId, id, value, expiresAt);
    }

    public boolean delete(long ownerId, UUID id) {
        return store.delete(ownerId, id);
    }
}
