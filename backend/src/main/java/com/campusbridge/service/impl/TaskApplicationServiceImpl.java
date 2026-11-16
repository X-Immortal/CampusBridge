package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreateTaskApplicationRequest;
import com.campusbridge.dto.view.MyTaskApplicationView;
import com.campusbridge.dto.view.TaskApplicationCandidateView;
import com.campusbridge.entity.CampusVerification;
import com.campusbridge.entity.Task;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.entity.User;
import com.campusbridge.entity.UserCredit;
import com.campusbridge.entity.UserProfile;
import com.campusbridge.mapper.CampusVerificationMapper;
import com.campusbridge.mapper.TaskApplicationMapper;
import com.campusbridge.mapper.TaskMapper;
import com.campusbridge.mapper.UserCreditMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.mapper.UserProfileMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.TaskApplicationService;
import com.campusbridge.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Transactional
@Service
@RequiredArgsConstructor
public class TaskApplicationServiceImpl implements TaskApplicationService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String VERIFICATION_STATUS_VERIFIED = "VERIFIED";
    private static final String HISTORY_NOTE_ACCEPTED = "APPLICATION_ACCEPTED";

    private final TaskApplicationMapper taskApplicationMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserCreditMapper userCreditMapper;
    private final CampusVerificationMapper campusVerificationMapper;
    private final TaskHistoryService taskHistoryService;
    private final NotificationService notificationService;

    @Override
    public TaskApplication createApplication(
            Long applicantId,
            Long taskId,
            CreateTaskApplicationRequest request) {
        validateApplicationIds(applicantId, taskId);

        Task task = requireTaskForUpdate(taskId);
        if (!STATUS_OPEN.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_002_INVALID_STATUS_TRANSITION);
        }
        if (applicantId.equals(task.getRequesterId())) {
            throw new BusinessException(
                    ErrorCode.TASK_003_PERMISSION_DENIED,
                    "Task requester cannot apply to own task");
        }
        TaskApplication existingApplication =
                taskApplicationMapper.selectByTaskIdAndApplicantId(taskId, applicantId);
        if (existingApplication != null && !STATUS_WITHDRAWN.equals(existingApplication.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_007_ALREADY_APPLIED);
        }

        if (existingApplication != null) {
            existingApplication.setMessage(request.getMessage());
            existingApplication.setStatus(STATUS_PENDING);
            existingApplication.setCreatedAt(LocalDateTime.now());
            existingApplication.setHandledAt(null);
            if (taskApplicationMapper.updateById(existingApplication) != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to reactivate task application");
            }
            notificationService.notifyTaskApplicationCreated(
                    task,
                    existingApplication,
                    applicantId);
            return existingApplication;
        }

        TaskApplication application = new TaskApplication();
        application.setTaskId(taskId);
        application.setApplicantId(applicantId);
        application.setMessage(request.getMessage());
        application.setStatus(STATUS_PENDING);
        application.setCreatedAt(LocalDateTime.now());
        application.setHandledAt(null);

        try {
            int affectedRows = taskApplicationMapper.insert(application);
            if (affectedRows != 1) {
                throw new BusinessException(
                        ErrorCode.COMMON_999_INTERNAL_ERROR,
                        "Failed to create task application");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.TASK_007_ALREADY_APPLIED);
        }
        notificationService.notifyTaskApplicationCreated(task, application, applicantId);
        return application;
    }

    @Override
    public List<TaskApplicationCandidateView> listApplications(
            Long requesterId,
            Long taskId) {
        Task task = requireTask(taskId);
        requireRequester(task, requesterId);

        return taskApplicationMapper.selectByTaskId(taskId)
                .stream()
                .map(this::toCandidateView)
                .toList();
    }

    @Override
    public List<MyTaskApplicationView> listMyApplications(Long applicantId) {
        if (applicantId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }

        return taskApplicationMapper.selectByApplicantId(applicantId)
                .stream()
                .map(application -> new MyTaskApplicationView(
                        application,
                        taskMapper.selectDetailById(application.getTaskId())))
                .filter(view -> view.getTask() != null)
                .toList();
    }

    @Override
    public TaskApplication withdrawApplication(Long applicantId, Long applicationId) {
        if (applicantId == null || applicationId == null) {
            throwValidation("applicantId and applicationId are required");
        }

        TaskApplication application = taskApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.TASK_004_APPLICATION_NOT_FOUND);
        }
        if (!applicantId.equals(application.getApplicantId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Only pending application can be withdrawn");
        }

        Task task = requireTask(application.getTaskId());

        LocalDateTime handledAt = LocalDateTime.now();
        int affectedRows = taskApplicationMapper.updateStatusById(
                applicationId,
                STATUS_PENDING,
                STATUS_WITHDRAWN,
                handledAt);
        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Application status changed before withdrawal");
        }

        application.setStatus(STATUS_WITHDRAWN);
        application.setHandledAt(handledAt);
        notificationService.notifyTaskApplicationWithdrawn(task, application, applicantId);
        return application;
    }

    @Override
    public TaskApplication acceptApplication(
            Long requesterId,
            Long taskId,
            Long applicationId) {
        if (requesterId == null || taskId == null || applicationId == null) {
            throwValidation("requesterId, taskId and applicationId are required");
        }

        Task task = requireTaskForUpdate(taskId);
        requireRequester(task, requesterId);

        TaskApplication application = taskApplicationMapper.selectById(applicationId);
        if (application == null || !taskId.equals(application.getTaskId())) {
            throw new BusinessException(ErrorCode.TASK_004_APPLICATION_NOT_FOUND);
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Only pending application can be accepted");
        }

        LocalDateTime now = LocalDateTime.now();
        int taskUpdated = taskMapper.acceptProviderById(
                taskId,
                requesterId,
                application.getApplicantId(),
                now);
        if (taskUpdated != 1) {
            throw new BusinessException(ErrorCode.TASK_008_ALREADY_ACCEPTED);
        }

        int applicationUpdated = taskApplicationMapper.updateStatusById(
                applicationId,
                STATUS_PENDING,
                STATUS_ACCEPTED,
                now);
        if (applicationUpdated != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_003_CONFLICT,
                    "Failed to accept pending application");
        }

        List<TaskApplication> rejectedApplications = taskApplicationMapper.selectByTaskId(taskId)
                .stream()
                .filter(item -> STATUS_PENDING.equals(item.getStatus()))
                .filter(item -> !applicationId.equals(item.getId()))
                .toList();
        taskApplicationMapper.rejectOtherPendingByTaskId(taskId, applicationId, now);
        taskHistoryService.recordHistory(
                taskId,
                STATUS_OPEN,
                STATUS_ACCEPTED,
                requesterId,
                HISTORY_NOTE_ACCEPTED);

        application.setStatus(STATUS_ACCEPTED);
        application.setHandledAt(now);
        notificationService.notifyTaskApplicationAccepted(task, application, requesterId);
        rejectedApplications.forEach(rejected ->
                notificationService.notifyTaskApplicationRejected(task, rejected, requesterId));
        return application;
    }

    private void validateApplicationIds(Long applicantId, Long taskId) {
        if (applicantId == null || taskId == null) {
            throwValidation("applicantId and taskId are required");
        }
    }

    private Task requireTaskForUpdate(Long taskId) {
        Task task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        return task;
    }

    private Task requireTask(Long taskId) {
        if (taskId == null) {
            throwValidation("taskId is required");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_001_NOT_FOUND);
        }
        return task;
    }

    private void requireRequester(Task task, Long requesterId) {
        if (requesterId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
        if (!requesterId.equals(task.getRequesterId())) {
            throw new BusinessException(ErrorCode.TASK_003_PERMISSION_DENIED);
        }
    }

    private TaskApplicationCandidateView toCandidateView(TaskApplication application) {
        Long applicantId = application.getApplicantId();
        User user = userMapper.selectById(applicantId);
        UserProfile profile = userProfileMapper.selectByUserId(applicantId);
        UserCredit credit = userCreditMapper.selectByUserId(applicantId);
        CampusVerification verification =
                campusVerificationMapper.selectLatestByUserId(applicantId);

        return new TaskApplicationCandidateView(
                application.getId(),
                applicantId,
                user == null ? null : user.getUsername(),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getCampus(),
                profile == null ? null : profile.getCollege(),
                verification != null
                        && VERIFICATION_STATUS_VERIFIED.equals(verification.getStatus()),
                credit == null || credit.getLevel() == null ? 1 : credit.getLevel(),
                credit == null || credit.getPoints() == null ? 0 : credit.getPoints(),
                application.getMessage(),
                application.getStatus(),
                application.getCreatedAt());
    }

    private void throwValidation(String message) {
        throw new BusinessException(ErrorCode.COMMON_001_VALIDATION_ERROR, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
