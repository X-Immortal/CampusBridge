package com.campusbridge.unit;

import com.campusbridge.entity.Task;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.enums.*;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.impl.TaskNotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskNotificationServiceImplTest {
    private final NotificationService notifications = mock(NotificationService.class);
    private final TaskNotificationServiceImpl service = new TaskNotificationServiceImpl(notifications, mock(UserMapper.class));

    @Test
    void completionNotifiesOtherParticipantWithExplicitEvent() {
        Task task = task();
        service.notifyTaskStatusChanged(task, TaskStatus.COMPLETED, 1L, null);
        verify(notifications).create(eq(2L), eq(NotificationType.TASK_COMPLETED), eq("任务已完成"),
                contains("任务已完成"), eq(9L));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void acceptedApplicationAndReminderHaveDistinctTypes() {
        Task task = task();
        TaskApplication application = new TaskApplication();
        application.setApplicantId(2L);
        service.notifyTaskApplicationAccepted(task, application, 1L);
        service.notifyTaskConfirmationReminder(task);
        verify(notifications).create(eq(2L), eq(NotificationType.TASK_APPLICATION_ACCEPTED), anyString(),
                anyString(), eq(9L));
        verify(notifications).create(eq(1L), eq(NotificationType.TASK_CONFIRMATION_REMINDER), anyString(),
                contains("确认"), eq(9L));
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN,TASK_PUBLISHED,任务已开放接单",
            "PENDING_REVIEW,TASK_PENDING_REVIEW,任务等待审核",
            "ACCEPTED,TASK_ACCEPTED,任务已选定帮助者",
            "IN_PROGRESS,TASK_STARTED,任务正在进行",
            "PENDING_CONFIRMATION,TASK_PENDING_CONFIRMATION,任务等待完成确认",
            "COMPLETED,TASK_COMPLETED,任务已完成",
            "REVIEWED,TASK_REVIEWED,任务已评价",
            "CANCELLED,TASK_CANCELLED,任务已取消",
            "DISPUTED,TASK_DISPUTED,任务进入争议处理",
            "REJECTED,TASK_REJECTED,任务审核未通过"
    })
    void statusNotificationPreservesEventTitleAndReason(TaskStatus status, NotificationType type, String title) {
        service.notifyTaskStatusChanged(task(), status, 1L, "  原因说明  ");
        verify(notifications).create(2L, type, title,
                "任务 #9“校园代取”：" + title + "。原因：原因说明", 9L);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void applicationAndReviewTemplatesPreserveTheirDynamicValues() {
        Task task = task();
        TaskApplication application = new TaskApplication();
        application.setApplicantId(2L);
        service.notifyTaskApplicationCreated(task, application, 2L);
        service.notifyTaskApplicationRejected(task, application, 1L);
        service.notifyTaskApplicationWithdrawn(task, application, 2L);
        Review review = new Review();
        review.setRevieweeId(2L);
        review.setRating(5);
        service.notifyTaskReviewCreated(task, review, 1L);

        verify(notifications).create(1L, NotificationType.TASK_APPLICATION_CREATED, "收到接单申请",
                "任务 #9“校园代取”收到来自用户 #2的接单申请。", 9L);
        verify(notifications).create(2L, NotificationType.TASK_APPLICATION_REJECTED, "接单申请未被选中",
                "你对任务 #9“校园代取”的接单申请未被选中。", 9L);
        verify(notifications).create(1L, NotificationType.TASK_APPLICATION_WITHDRAWN, "接单申请已撤回",
                "用户 #2撤回了对任务 #9“校园代取”的接单申请。", 9L);
        verify(notifications).create(2L, NotificationType.TASK_REVIEWED, "收到任务评价",
                "任务 #9“校园代取”收到了一条5星评价。", 9L);
    }

    @Test
    void openTimeoutUsesReminderTemplateAndInvalidStatesDoNotSend() {
        service.notifyOpenTaskTimeoutReminder(task());
        verify(notifications).create(1L, NotificationType.TASK_OPEN_TIMEOUT, "任务已超过接单截止时间",
                "任务 #9“校园代取”已超过截止时间且还没有被接单。系统将在宽限期后自动取消该任务。在此之前，你可以编辑、重发或手动取消。", 9L);
        assertThatThrownBy(() -> service.notifyTaskStatusChanged(task(), TaskStatus.DRAFT, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(notifications);
    }

    private Task task() {
        Task task = new Task();
        task.setId(9L);
        task.setTitle("校园代取");
        task.setRequesterId(1L);
        task.setProviderId(2L);
        return task;
    }
}
