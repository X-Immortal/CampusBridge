package com.campusbridge.dto.response;

import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.CampusVerification;
import com.campusbridge.pojo.CompletionProof;
import com.campusbridge.pojo.Conversation;
import com.campusbridge.pojo.Message;
import com.campusbridge.pojo.ModerationRecord;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.PostInteraction;
import com.campusbridge.pojo.Report;
import com.campusbridge.pojo.Reply;
import com.campusbridge.pojo.Review;
import com.campusbridge.pojo.Task;
import com.campusbridge.pojo.TaskApplication;
import com.campusbridge.pojo.User;
import com.campusbridge.pojo.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class ResponseDtos {
    private ResponseDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiMeta {
        private String requestId;
        private LocalDateTime timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private String code;
        private String message;
        private T data;
        private ApiMeta meta;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedResult<T> {
        private List<T> items;
        private Integer page;
        private Integer size;
        private Long total;
    }

    public static class ApiResponseAuthResult extends ApiResponse<ViewDtos.AuthResult> {
    }

    public static class ApiResponseUser extends ApiResponse<User> {
    }

    public static class ApiResponseUserProfile extends ApiResponse<UserProfile> {
    }

    public static class ApiResponseUserHomepageView extends ApiResponse<ViewDtos.UserHomepageView> {
    }

    public static class ApiResponseCampusVerification extends ApiResponse<CampusVerification> {
    }

    public static class ApiResponsePost extends ApiResponse<Post> {
    }

    public static class ApiResponseReply extends ApiResponse<Reply> {
    }

    public static class ApiResponsePostInteraction extends ApiResponse<PostInteraction> {
    }

    public static class ApiResponseTask extends ApiResponse<Task> {
    }

    public static class ApiResponseTaskDetailView extends ApiResponse<ViewDtos.TaskDetailView> {
    }

    public static class ApiResponsePostDetailView extends ApiResponse<ViewDtos.PostDetailView> {
    }

    public static class ApiResponseTaskApplication extends ApiResponse<TaskApplication> {
    }

    public static class ApiResponseTaskApplicationList extends ApiResponse<List<TaskApplication>> {
    }

    public static class ApiResponseCompletionProof extends ApiResponse<CompletionProof> {
    }

    public static class ApiResponseReview extends ApiResponse<Review> {
    }

    public static class ApiResponseReviewList extends ApiResponse<List<Review>> {
    }

    public static class ApiResponseConversation extends ApiResponse<Conversation> {
    }

    public static class ApiResponseMessage extends ApiResponse<Message> {
    }

    public static class ApiResponseAttachment extends ApiResponse<Attachment> {
    }

    public static class ApiResponseReport extends ApiResponse<Report> {
    }

    public static class ApiResponseModerationRecord extends ApiResponse<ModerationRecord> {
    }

    public static class ApiResponsePagedContentItemView extends ApiResponse<PagedResult<ViewDtos.ContentItemView>> {
    }

    public static class ApiResponsePagedPost extends ApiResponse<PagedResult<Post>> {
    }

    public static class ApiResponsePagedTask extends ApiResponse<PagedResult<Task>> {
    }

    public static class ApiResponsePagedConversation extends ApiResponse<PagedResult<Conversation>> {
    }

    public static class ApiResponsePagedMessage extends ApiResponse<PagedResult<Message>> {
    }

    public static class ApiResponsePagedReport extends ApiResponse<PagedResult<Report>> {
    }

    public static class ApiResponseTaskList extends ApiResponse<List<Task>> {
    }
}
