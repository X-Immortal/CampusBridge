package com.campusbridge.service.impl;

import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.BusinessException;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.ReviewMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.pojo.Review;
import com.campusbridge.pojo.Task;
import com.campusbridge.service.ReviewService;
import com.campusbridge.service.UserCreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final TaskMapper taskMapper;
    private final UserCreditService userCreditService;

    private static final int MAX_COMMENT_LENGTH = 500;

    public ReviewServiceImpl(ReviewMapper reviewMapper, TaskMapper taskMapper, UserCreditService userCreditService) {
        this.reviewMapper = reviewMapper;
        this.taskMapper = taskMapper;
        this.userCreditService = userCreditService;
    }

    @Override
    public Review createReview(Long reviewerId, Long taskId, RequestDtos.CreateReviewRequest request) {
        requireReviewerId(reviewerId);
        requireTaskId(taskId);
        validateCreateReviewRequest(request);

        // 获取任务信息
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        // 确定被评价者（评价者的对方）
        Long revieweeId = reviewerId.equals(task.getRequesterId()) ? task.getProviderId() : task.getRequesterId();
        if (revieweeId == null) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION,
                    "Task has not been accepted yet");
        }

        // 检查是否已经评价过
        Review existingReview = reviewMapper.selectByTaskAndReviewer(taskId, reviewerId, revieweeId);
        if (existingReview != null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "You have already reviewed this user for this task");
        }

        // 创建评价
        Review review = new Review();
        review.setTaskId(taskId);
        review.setReviewerId(reviewerId);
        review.setRevieweeId(revieweeId);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setCreatedAt(LocalDateTime.now());

        int affectedRows = reviewMapper.insert(review);
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR, "Failed to create review");
        }

        // 更新被评价者的信用分
        userCreditService.updateCreditByReview(revieweeId, request.getRating(), taskId);

        return review;
    }

    @Override
    public List<Review> listReviews(Long taskId) {
        requireTaskId(taskId);

        // 验证任务存在
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }

        return reviewMapper.selectByTaskId(taskId);
    }

    private void requireReviewerId(Long reviewerId) {
        if (reviewerId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reviewerId is required");
        }
    }

    private void requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId is required");
        }
    }

    private void validateCreateReviewRequest(RequestDtos.CreateReviewRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "request is required");
        }
        if (request.getRating() == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "rating is required");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "rating must be between 1 and 5");
        }
        if (request.getComment() != null && request.getComment().length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "comment must not exceed " + MAX_COMMENT_LENGTH + " characters");
        }
    }
}
