package com.campusbridge.service.impl;

import com.campusbridge.common.ErrorCode;
import com.campusbridge.common.BusinessException;
import com.campusbridge.mapper.CreditRecordMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.pojo.CreditRecord;
import com.campusbridge.pojo.UserCredit;
import com.campusbridge.service.UserCreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserCreditServiceImpl implements UserCreditService {

    private final UserCreditMapper userCreditMapper;
    private final CreditRecordMapper creditRecordMapper;

    // 信用分计算规则
    private static final int BASE_CREDIT_SCORE = 100;
    private static final int REVIEW_5_SCORE = 5;
    private static final int REVIEW_4_SCORE = 3;
    private static final int REVIEW_3_SCORE = 0;
    private static final int REVIEW_2_SCORE = -5;
    private static final int REVIEW_1_SCORE = -10;
    private static final int TASK_COMPLETION_SCORE = 2;
    private static final int MAX_CREDIT_SCORE = 200;
    private static final int MIN_CREDIT_SCORE = 0;

    public UserCreditServiceImpl(UserCreditMapper userCreditMapper, CreditRecordMapper creditRecordMapper) {
        this.userCreditMapper = userCreditMapper;
        this.creditRecordMapper = creditRecordMapper;
    }

    @Override
    public UserCredit getUserCredit(Long userId) {
        requireUserId(userId);
        
        UserCredit userCredit = userCreditMapper.selectByUserId(userId);
        if (userCredit == null) {
            // 初始化用户信用信息
            userCredit = new UserCredit();
            userCredit.setUserId(userId);
            userCredit.setPoints(0);
            userCredit.setLevel(1);
            userCredit.setCreditScore(BASE_CREDIT_SCORE);
            userCredit.setUpdatedAt(LocalDateTime.now());
            userCreditMapper.insert(userCredit);
        }
        
        return userCredit;
    }

    @Override
    public List<CreditRecord> getCreditRecords(Long userId, Integer page, Integer size) {
        requireUserId(userId);
        
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20;
        }
        
        int offset = (page - 1) * size;
        return creditRecordMapper.selectByUserId(userId, offset, size);
    }

    @Override
    public void updateCreditByReview(Long revieweeId, Integer rating, Long taskId) {
        requireUserId(revieweeId);
        requireRating(rating);
        requireTaskId(taskId);
        
        // 根据评分计算信用分变化
        int scoreChange = calculateScoreByRating(rating);
        
        if (scoreChange == 0) {
            // 3星评价不影响信用分
            return;
        }
        
        updateCreditScore(revieweeId, scoreChange, "REVIEW", taskId, 
                "Task " + taskId + " review rating: " + rating);
    }

    @Override
    public void updateCreditByTaskCompletion(Long userId, Long taskId) {
        requireUserId(userId);
        requireTaskId(taskId);
        
        updateCreditScore(userId, TASK_COMPLETION_SCORE, "TASK_COMPLETION", taskId,
                "Task " + taskId + " completed");
    }

    @Override
    public void updateCreditByReport(Long userId, Integer scoreChange, String reason, Long reportId) {
        requireUserId(userId);
        requireReason(reason);
        
        if (scoreChange == null || scoreChange == 0) {
            return;
        }
        
        String sourceType = scoreChange < 0 ? "REPORT_PUNISHMENT" : "REPORT_APPEAL_SUCCESS";
        updateCreditScore(userId, scoreChange, sourceType, reportId, reason);
    }

    /**
     * 更新信用分并记录变化
     */
    private void updateCreditScore(Long userId, int scoreChange, String sourceType, 
                                   Long sourceId, String reason) {
        // 获取当前信用信息
        UserCredit userCredit = getUserCredit(userId);
        int currentScore = userCredit.getCreditScore() != null ? userCredit.getCreditScore() : BASE_CREDIT_SCORE;
        
        // 计算新分数（限制在合理范围内）
        int newScore = Math.max(MIN_CREDIT_SCORE, Math.min(MAX_CREDIT_SCORE, currentScore + scoreChange));
        
        // 更新信用分
        userCreditMapper.updateCreditScore(userId, newScore, LocalDateTime.now());
        
        // 记录信用分变化
        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setChangeValue(scoreChange);
        record.setReason(reason);
        record.setSourceType(sourceType);
        record.setSourceId(sourceId);
        record.setCreatedAt(LocalDateTime.now());
        
        creditRecordMapper.insert(record);
    }

    /**
     * 根据评价等级计算信用分变化
     */
    private int calculateScoreByRating(Integer rating) {
        switch (rating) {
            case 5:
                return REVIEW_5_SCORE;
            case 4:
                return REVIEW_4_SCORE;
            case 3:
                return REVIEW_3_SCORE;
            case 2:
                return REVIEW_2_SCORE;
            case 1:
                return REVIEW_1_SCORE;
            default:
                throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                        "Invalid rating: " + rating + ". Must be between 1 and 5.");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "userId is required");
        }
    }

    private void requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "taskId is required");
        }
    }

    private void requireRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "rating must be between 1 and 5");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, "reason is required");
        }
        if (reason.length() > 300) {
            throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR,
                    "reason must not exceed 300 characters");
        }
    }
}
