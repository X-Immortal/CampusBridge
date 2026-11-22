package com.campusbridge.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PersonalMemory(UUID id, long userId, MemoryType type, String value, Instant expiresAt) {

    public PersonalMemory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (userId <= 0 || value == null || value.isBlank()) {
            throw new IllegalArgumentException("A positive userId and non-blank value are required");
        }
        if ((type == MemoryType.TEMPORARY) != (expiresAt != null)) {
            throw new IllegalArgumentException("Only temporary memory requires expiresAt");
        }
    }

    public boolean isInferred() {
        return type == MemoryType.INFERRED;
    }
}
