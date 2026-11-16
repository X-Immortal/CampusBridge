package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateReviewRequest;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.Task;
import com.campusbridge.enums.TaskStatus;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.TaskNotificationService;
import com.campusbridge.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BE16ReviewServiceImplTest {
    private static final Long REQUESTER_ID = 1L;
    private static final Long PROVIDER_ID = 2L;
    private static final Long TASK_ID = 10L;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskNotificationService notificationService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewMapper, taskMapper, notificationService);
    }

    // ---- createReview ----

    @Test
    void createReviewSucceedsForCompletedTask() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "COMPLETED");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.emptyList());
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);

        Review review = reviewService.createReview(
                REQUESTER_ID, TASK_ID, reviewRequest(PROVIDER_ID, 5, "很棒"));

        assertThat(review.getTaskId()).isEqualTo(TASK_ID);
        assertThat(review.getReviewerId()).isEqualTo(REQUESTER_ID);
        assertThat(review.getRating()).isEqualTo(5);
        verify(notificationService).notifyTaskReviewCreated(task, review, REQUESTER_ID);
    }

    @Test
    void createReviewSucceedsForReviewedTask() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "REVIEWED");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(Collections.emptyList());
        when(reviewMapper.insert(any(Review.class))).thenReturn(1);

        Review review = reviewService.createReview(
                PROVIDER_ID, TASK_ID, reviewRequest(REQUESTER_ID, 4, "谢谢"));

        assertThat(review.getRating()).isEqualTo(4);
    }

    @Test
    void createReviewRejectsNonParticipant() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "COMPLETED");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> reviewService.createReview(
                999L, TASK_ID, reviewRequest(PROVIDER_ID, 3, "not involved")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_003_PERMISSION_DENIED);
    }

    @Test
    void createReviewRejectsNonCompletedTask() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "IN_PROGRESS");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> reviewService.createReview(
                REQUESTER_ID, TASK_ID, reviewRequest(PROVIDER_ID, 3, "early")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
    }

    @Test
    void createReviewRejectsDuplicateReview() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "COMPLETED");
        Review existing = new Review();
        existing.setTaskId(TASK_ID);
        existing.setReviewerId(REQUESTER_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> reviewService.createReview(
                REQUESTER_ID, TASK_ID, reviewRequest(PROVIDER_ID, 4, "again")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_001_DUPLICATE_REVIEW);
    }

    @Test
    void createReviewRejectsTaskNotFound() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.createReview(
                REQUESTER_ID, TASK_ID, reviewRequest(PROVIDER_ID, 5, "comment")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_001_NOT_FOUND);
    }

    // ---- listReviews ----

    @Test
    void listReviewsReturnsReviewsForTask() {
        Task task = task(TASK_ID, REQUESTER_ID, PROVIDER_ID, "COMPLETED");
        Review review = new Review();
        review.setTaskId(TASK_ID);
        review.setReviewerId(REQUESTER_ID);
        review.setRating(5);

        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(reviewMapper.selectByTaskId(TASK_ID)).thenReturn(List.of(review));

        List<Review> results = reviewService.listReviews(TASK_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getReviewerId()).isEqualTo(REQUESTER_ID);
    }

    @Test
    void listReviewsRejectsNullTaskId() {
        assertThatThrownBy(() -> reviewService.listReviews(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_001_VALIDATION_ERROR);
    }

    // ---- helpers ----

    private CreateReviewRequest reviewRequest(Long revieweeId, Integer rating, String comment) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRevieweeId(revieweeId);
        request.setRating(rating);
        request.setComment(comment);
        return request;
    }

    private Task task(Long id, Long requesterId, Long providerId, String status) {
        Task task = new Task();
        task.setId(id);
        task.setRequesterId(requesterId);
        task.setProviderId(providerId);
        task.setStatus(TaskStatus.valueOf(status));
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
