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

    public List<PersonalMemory> list(long ownerId) {
        return store.list(ownerId);
    }

    public boolean delete(long ownerId, UUID id) {
        return store.delete(ownerId, id);
    }
}
