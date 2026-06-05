package com.campusbridge.dto.request;

import com.campusbridge.dto.enums.ApiEnums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        public String username;
        public String password;
        public String phone;
        public String email;
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        public String username;
        public String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserProfileRequest {
        public String nickname;
        public String bio;
        public String campus;
        public String college;
        public String contactInfo;
        public String avatarUrl;
        public List<String> interestTags;
        public List<String> skillTags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateVerificationRequest {
        public ApiEnums.VerificationMethod method;
        public String realName;
        public String studentNumber;
        public String school;
        public String department;
        public String campusEmail;
        public String evidenceUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmEmailVerificationRequest {
        public String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        public ApiEnums.PostCategory category;
        public String title;
        public String content;
        public List<Long> attachmentIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        public String title;
        public String content;
        public ApiEnums.PostStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReplyRequest {
        public Long parentReplyId;
        public String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostInteractionRequest {
        public ApiEnums.PostInteractionType type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTaskRequest {
        public ApiEnums.TaskCategory category;
        public String title;
        public String description;
        public String campus;
        public String location;
        public LocalDateTime deadline;
        public ApiEnums.RewardType rewardType;
        public String rewardDescription;
        public String completionStandard;
        public Boolean requiresProof;
        public Boolean onlyInAppContact;
        public Boolean allowOfflineContact;
        public Boolean isInstantTask;
        public String routeHint;
        public Boolean riskDisclosureConfirmed;
        public String sensitiveReason;
        public Integer rewardAmount;
        public List<Long> attachmentIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTaskRequest {
        public String title;
        public String description;
        public LocalDateTime deadline;
        public ApiEnums.RewardType rewardType;
        public String rewardDescription;
        public Integer rewardAmount;
        public String completionStandard;
        public Boolean requiresProof;
        public Boolean onlyInAppContact;
        public Boolean allowOfflineContact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTaskApplicationRequest {
        public String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitCompletionRequest {
        public String description;
        public List<Long> attachmentIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReviewRequest {
        public Long revieweeId;
        public Integer rating;
        public String comment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateConversationRequest {
        public String relatedType;
        public Long relatedId;
        public Long peerUserId;
        public String initialMessage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        public ApiEnums.MessageType type;
        public String content;
        public List<Long> attachmentIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadAttachmentRequest {
        public MultipartFile file;
        public ApiEnums.AttachmentOwnerType expectedOwnerType;
        public ApiEnums.AttachmentType type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReportRequest {
        public ApiEnums.ReportTargetType targetType;
        public Long targetId;
        public String reason;
        public String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolveReportRequest {
        public ApiEnums.ModerationStatus status;
        public String decision;
        public Long punishedUserId;
        public Integer creditChange;
        public Boolean markTaskDisputed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatusTransitionRequest {
        public ApiEnums.TaskStatusTransitionAction action;
        public String reason;
    }
}
