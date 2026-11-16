package com.campusbridge.unit;

import com.campusbridge.common.GuestTaskMasker;
import com.campusbridge.dto.view.TaskView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestTaskMaskerTest {

    @Test
    void guestRequestMasksSensitiveTaskFields() {
        TaskView task = sampleTask();

        TaskView masked = GuestTaskMasker.maskForGuest(task, null);

        assertThat(masked).isNotNull();
        assertThat(masked.getTitle()).isEqualTo("帮取快递");
        assertThat(masked.getRequesterId()).isEqualTo(100L);
        assertThat(masked.getProviderId()).isNull();
        assertThat(masked.getProviderName()).isNull();
        assertThat(masked.getRouteHint()).isNull();
        assertThat(masked.getSensitiveReason()).isNull();
    }

    @Test
    void authenticatedRequestLeavesTaskUntouched() {
        TaskView task = sampleTask();

        TaskView result = GuestTaskMasker.maskForGuest(task, 42L);

        assertThat(result).isSameAs(task);
        assertThat(result.getProviderId()).isEqualTo(999L);
        assertThat(result.getProviderName()).isEqualTo("已选帮助者");
        assertThat(result.getRouteHint()).isEqualTo("走西门");
        assertThat(result.getSensitiveReason()).isEqualTo("仅站内沟通");
    }

    @Test
    void nullTaskPassesThrough() {
        assertThat(GuestTaskMasker.maskForGuest(null, null)).isNull();
        assertThat(GuestTaskMasker.maskForGuest(null, 1L)).isNull();
    }

    @Test
    void isAnonymousReflectsUserIdPresence() {
        assertThat(GuestTaskMasker.isAnonymous(null)).isTrue();
        assertThat(GuestTaskMasker.isAnonymous(0L)).isFalse();
        assertThat(GuestTaskMasker.isAnonymous(7L)).isFalse();
    }

    private TaskView sampleTask() {
        TaskView task = new TaskView();
        task.setId(10L);
        task.setRequesterId(100L);
        task.setProviderId(999L);
        task.setProviderName("已选帮助者");
        task.setRouteHint("走西门");
        task.setSensitiveReason("仅站内沟通");
        task.setTitle("帮取快递");
        task.setStatus("OPEN");
        return task;
    }
}
