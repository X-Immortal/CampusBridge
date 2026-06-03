package com.campusbridge.dto.view;

import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Report;
import com.campusbridge.pojo.Reply;
import com.campusbridge.pojo.Review;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.TaskApplication;
import com.campusbridge.pojo.TaskHistory;
import com.campusbridge.pojo.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class ViewDtos {
    private ViewDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResult {
        private String token;
        private String refreshToken;
        private User user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserHomepageView {
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private String bio;
        private String campus;
        private String college;
        private Boolean verified;
        private Integer creditLevel;
        private Integer points;
        private List<String> interestTags;
        private List<String> skillTags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentItemView {
        private ApiEnums.ContentItemType itemType;
        private Long sourceId;
        private String title;
        private String summary;
        private Long authorId;
        private String authorName;
        private String categoryName;
        private String statusLabel;
        private LocalDateTime createdAt;
        private LocalDateTime sortTime;
        private String rewardLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDetailView {
        private Task task;
        private List<TaskApplication> applications;
        private CompletionProof completionProof;
        private List<TaskHistory> history;
        private List<Review> reviews;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailView {
        private Post post;
        private List<Reply> replies;
        private Boolean likedByCurrentUser;
        private Boolean markedByCurrentUser;
        private Long acceptedReplyId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessageView {
        private Conversation conversation;
        private List<Message> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportListItemView {
        private Report report;
        private String handlerName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserView {
        private Long id;
        private String username;
        private String role;
        private Boolean enabled;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskApplicationCandidateView {
        private Long applicationId;
        private Long applicantId;
        private String username;
        private String nickname;
        private String avatarUrl;
        private String bio;
        private String campus;
        private String college;
        private Boolean verified;
        private Integer creditLevel;
        private Integer points;
        private String message;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileView {
        private Long userId;
        private String nickname;
        private String bio;
        private String campus;
        private String college;
        private String avatarUrl;
        private String contactInfo;
        private List<String> interestTags;
        private List<String> skillTags;
        private Boolean verified;
        private Integer creditLevel;
        private Integer points;
    }
}
