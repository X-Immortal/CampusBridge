package com.campusbridge.memory;

import com.campusbridge.common.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory")
@ConditionalOnProperty(name = "memory.http.enabled", havingValue = "true")
public class MemoryController {

    private final MemoryService service;
    private final CurrentUserResolver currentUser;

    public MemoryController(MemoryService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<PersonalMemory>> list(
            HttpServletRequest request, @RequestParam(required = false) Long userId) {
        long ownerId = currentUser.requireCurrentUserId(request);
        if (userId != null && userId != ownerId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(service.list(ownerId));
    }

    @PostMapping
    public ResponseEntity<PersonalMemory> add(
            HttpServletRequest request, @RequestBody CreateMemoryRequest body) {
        long ownerId = currentUser.requireCurrentUserId(request);
        if (body.userId() != null && body.userId() != ownerId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (body.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        PersonalMemory memory = service.add(ownerId, body.type(), body.value(), body.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(memory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        long ownerId = currentUser.requireCurrentUserId(request);
        if (!service.delete(ownerId, id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    public record CreateMemoryRequest(Long userId, MemoryType type, String value, Instant expiresAt) {
    }
}
