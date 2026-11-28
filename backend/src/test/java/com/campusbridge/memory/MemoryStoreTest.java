package com.campusbridge.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    private final Clock clock = mock(Clock.class);
    private final MemoryService service = new MemoryService(new MemoryStore(clock));

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void temporaryMemoryDisappearsAtExpiry() {
        Instant expiry = NOW.plusSeconds(60);
        PersonalMemory memory = service.add(1, MemoryType.TEMPORARY, "本次配送地点", expiry);
        assertThat(service.list(1)).containsExactly(memory);

        when(clock.instant()).thenReturn(expiry);
        assertThat(service.list(1)).isEmpty();
        assertThat(service.delete(1, memory.id())).isFalse();
    }

    @Test
    void inferredMarkerSurvivesStorageAndSerialization() throws Exception {
        service.add(1, MemoryType.INFERRED, "可能偏好站内联系", null);
        PersonalMemory memory = service.list(1).get(0);
        assertThat(memory.isInferred()).isTrue();
        assertThat(new ObjectMapper().valueToTree(memory).path("inferred").asBoolean()).isTrue();
    }

    @Test
    void onlyOwnerCanReadOrWithdrawDeclaredMemory() {
        PersonalMemory memory = service.add(1, MemoryType.DECLARED, "偏好站内联系", null);
        assertThat(memory.isInferred()).isFalse();
        assertThat(service.list(2)).isEmpty();
        assertThat(service.delete(2, memory.id())).isFalse();
        assertThat(service.list(1)).containsExactly(memory);
        assertThat(service.delete(1, memory.id())).isTrue();
        assertThat(service.list(1)).isEmpty();
        assertThat(service.delete(1, memory.id())).isFalse();
    }

    @Test
    void declaredMemoryCanBeUpdatedAndStaysWithOwner() {
        PersonalMemory memory = service.add(1, MemoryType.DECLARED, "偏好站内联系", null);
        PersonalMemory updated = service.update(1, memory.id(), "只接受站内联系", null);
        assertThat(updated).isNotNull();
        assertThat(updated.value()).isEqualTo("只接受站内联系");
        assertThat(service.list(1)).containsExactly(updated);
        assertThat(service.update(2, memory.id(), "别人改的", null)).isNull();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.update(1, memory.id(), "声明不能带过期时间", NOW.plusSeconds(60)));
    }

    @Test
    void temporaryMemoryCanBeRenewed() {
        PersonalMemory memory = service.add(1, MemoryType.TEMPORARY, "本次地点", NOW.plusSeconds(60));
        PersonalMemory renewed = service.update(1, memory.id(), "本次地点（续期）", NOW.plusSeconds(120));
        assertThat(renewed).isNotNull();
        assertThat(renewed.expiresAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(service.list(1)).containsExactly(renewed);
    }

    @Test
    void rejectsMissingExpiredOrInapplicableExpiry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.add(1, MemoryType.TEMPORARY, "上下文", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.add(1, MemoryType.TEMPORARY, "上下文", NOW));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.add(1, MemoryType.DECLARED, "声明", NOW.plusSeconds(60)));
    }
}
