package com.campusbridge.unit;

import com.campusbridge.entity.Notification;
import com.campusbridge.enums.ContentItemType;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.mapper.NotificationMapper;
import com.campusbridge.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {
    @Test
    void notificationEventDeterminesPersistedRelatedType() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        when(mapper.insert(any(Notification.class))).thenReturn(1);
        var service = new NotificationServiceImpl(mapper);

        service.create(1L, NotificationType.POST_REPLY, "回复提醒", "正文", 10L);
        service.create(1L, NotificationType.TASK_COMPLETED, "任务完成", "正文", 20L);

        var records = ArgumentCaptor.forClass(Notification.class);
        verify(mapper, times(2)).insert(records.capture());
        assertThat(records.getAllValues()).extracting(Notification::getRelatedType)
                .containsExactly(ContentItemType.POST, ContentItemType.TASK);
        assertThat(records.getAllValues()).extracting(Notification::getRelatedId)
                .containsExactly(10L, 20L);
    }
}
