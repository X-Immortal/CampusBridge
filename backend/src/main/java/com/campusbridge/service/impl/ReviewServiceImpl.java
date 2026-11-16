package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateReviewRequest;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.Task;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReviewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Transactional
@Service
public class ReviewServiceImpl implements ReviewService {
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_REVIEWED = "REVIEWED";

    private final ReviewMapper reviewMapper;
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    public ReviewServiceImpl(
            ReviewMapper reviewMapper,
            TaskMapper taskMapper,
            NotificationService notificationService) {
        this.reviewMapper = reviewMapper;
        this.taskMapper = taskMapper;
        this.notificationService = notificationService;
    }

    @Override
    public Review createReview(Long reviewerId, Long taskId, CreateReviewRequest request) {
        if (reviewerId == null || taskId == null || request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "reviewerId, taskId and request body are required");
        }
        if (request.getRevieweeId() == null || request.getRating() == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "revieweeId and rating are required");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "rating must be between 1 and 5");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        String taskStatus = task.getStatus();
        if (!STATUS_COMPLETED.equals(taskStatus) && !STATUS_REVIEWED.equals(taskStatus)) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "Only completed tasks can be reviewed");
        }

        boolean isRequester = reviewerId.equals(task.getRequesterId());
        boolean isProvider = task.getProviderId() != null && reviewerId.equals(task.getProviderId());
        if (!isRequester && !isProvider) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED,
                    "Only task participants can review");
        }

        Long otherParticipantId = isRequester ? task.getProviderId() : task.getRequesterId();
        if (!request.getRevieweeId().equals(otherParticipantId)) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED,
                    "Only the other task participant can be reviewed");
        }
        if (request.getComment() != null && request.getComment().length() > 500) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "comment is too long");
        }

        List<Review> existingReviews = reviewMapper.selectByTaskId(taskId);
        boolean alreadyReviewed = existingReviews.stream()
                .anyMatch(r -> r.getTaskId().equals(taskId) && r.getReviewerId().equals(reviewerId));
        if (alreadyReviewed) {
            throw new BusinessException(ErrorCode.REVIEW_001_DUPLICATE_REVIEW);
        }

        Review review = new Review();
        review.setTaskId(taskId);
        review.setReviewerId(reviewerId);
        review.setRevieweeId(request.getRevieweeId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setCreatedAt(LocalDateTime.now());

        try {
            if (reviewMapper.insert(review) != 1) {
                throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to create review");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.REVIEW_001_DUPLICATE_REVIEW);
        }

        notificationService.notifyTaskReviewCreated(task, review, reviewerId);

        return review;
    }

    @Override
    public List<Review> listReviews(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "taskId is required");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        return reviewMapper.selectByTaskId(taskId);
    }

    @Override
    public List<Review> listReviewsByUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "userId is required");
        }
        return reviewMapper.selectByRevieweeId(userId);
    }
}
