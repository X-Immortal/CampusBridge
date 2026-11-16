package com.campusbridge.unit;

import com.campusbridge.enums.ReplyStatus;
import com.campusbridge.common.BusinessException;
import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.PostInteraction;
import com.campusbridge.entity.Reply;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
import com.campusbridge.enums.PostCategory;
import com.campusbridge.enums.PostStatus;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReplyNotificationService;
import com.campusbridge.service.impl.ContentModerationServiceImpl;
import com.campusbridge.service.impl.InteractionServiceImpl;
import com.campusbridge.service.impl.PostServiceImpl;
import com.campusbridge.service.impl.ReplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostIntegrityTest {
    private PostMapper posts;
    private ReplyMapper replies;
    private PostInteractionMapper interactions;
    private AttachmentMapper attachments;
    private PostServiceImpl service;
    private ReplyServiceImpl replyService;
    private InteractionServiceImpl interactionService;

    @BeforeEach
    void setUp() {
        posts = mock(PostMapper.class);
        replies = mock(ReplyMapper.class);
        interactions = mock(PostInteractionMapper.class);
        attachments = mock(AttachmentMapper.class);
        service = new PostServiceImpl(posts, replies, interactions, attachments, new ContentModerationServiceImpl());
        replyService = new ReplyServiceImpl(replies, posts, new ReplyNotificationService(mock(NotificationService.class)));
        interactionService = new InteractionServiceImpl(interactions, posts, replies);
    }

    @Test
    void editingCannotBypassPublishModeration() {
        when(posts.selectById(10L)).thenReturn(post(PostStatus.PUBLISHED));
        UpdatePostRequest request = new UpdatePostRequest("Updated title", "Updated content", null);
        request.setContent("代考");
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        verify(posts, never()).updateContent(any(), any());
    }

    @Test
    void authorCannotPublishModeratedPostOrSetReportStatus() {
        when(posts.selectById(10L)).thenReturn(post(PostStatus.HIDDEN), post(PostStatus.PUBLISHED));
        UpdatePostRequest request = new UpdatePostRequest("Updated title", "Updated content", null);
        request.setStatus(PostStatus.PUBLISHED);
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        request.setStatus(PostStatus.HIDDEN);
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        verify(posts, never()).updateContent(any(), any());
    }

    @Test
    void anonymousOrOtherUserCannotReadHiddenPost() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post(PostStatus.HIDDEN)));
        assertThatThrownBy(() -> service.getPostDetail(10L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getPostDetail(10L, 2L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void ownerCanReadHiddenPost() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post(PostStatus.HIDDEN)));
        assertThat(service.getPostDetail(10L, 1L).getPost().getId()).isEqualTo(10L);
    }

    @Test
    void deletedPostIsNotReadableEvenByAuthor() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post(PostStatus.DELETED)));
        assertThatThrownBy(() -> service.getPostDetail(10L, 1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void postAttachmentUsesAtomicTemporaryOwnershipBinding() {
        Attachment attachment = new Attachment();
        attachment.setId(3L);
        attachment.setUploaderId(1L);
        attachment.setOwnerType(AttachmentOwnerType.POST);
        attachment.setStatus(AttachmentStatus.TEMPORARY);
        when(attachments.selectById(3L)).thenReturn(attachment);
        when(attachments.bindTemporaryToOwner(3L, 1L, AttachmentOwnerType.POST, 10L)).thenReturn(1);
        when(posts.insert(any(Post.class))).thenAnswer(invocation -> {
            ((Post) invocation.getArgument(0)).setId(10L);
            return 1;
        });
        CreatePostRequest request = request();
        request.setAttachmentIds(List.of(3L));

        service.createPost(1L, request);

        verify(attachments).bindTemporaryToOwner(3L, 1L, AttachmentOwnerType.POST, 10L);
        verify(attachments, never()).updateById(any(Attachment.class));
    }

    @Test
    void replyUsesAtomicCountAndRejectsClosedPosts() {
        when(posts.selectById(10L)).thenReturn(post(PostStatus.PUBLISHED), post(PostStatus.CLOSED));
        when(replies.insert(any(Reply.class))).thenReturn(1);
        when(posts.incrementReplyCount(10L)).thenReturn(1);
        CreateReplyRequest request = new CreateReplyRequest();
        request.setContent("Reply");
        replyService.createReply(2L, 10L, request);
        verify(posts).incrementReplyCount(10L);
        verify(posts, never()).updateById(any(Post.class));
        assertThatThrownBy(() -> replyService.createReply(2L, 10L, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptingReplyDoesNotInsertUnsupportedInteractionType() {
        when(posts.selectById(10L)).thenReturn(post(PostStatus.PUBLISHED));
        Reply reply = new Reply();
        reply.setId(20L);
        reply.setPostId(10L);
        reply.setStatus(ReplyStatus.PUBLISHED);
        when(replies.selectById(20L)).thenReturn(reply);
        when(posts.updateAcceptedReply(10L, 20L)).thenReturn(1);

        var result = interactionService.acceptReply(1L, 10L, 20L);
        assertThat(result.postId()).isEqualTo(10L);
        assertThat(result.acceptedReplyId()).isEqualTo(20L);

        verify(posts).updateAcceptedReply(10L, 20L);
        verify(interactions, never()).insert(any(PostInteraction.class));
    }

    private Post post(PostStatus status) {
        Post post = new Post();
        post.setId(10L);
        post.setAuthorId(1L);
        post.setTitle("Question");
        post.setContent("Details");
        post.setCategory(PostCategory.INQUIRY_HELP);
        post.setStatus(status);
        return post;
    }

    private CreatePostRequest request() {
        CreatePostRequest request = new CreatePostRequest();
        request.setCategory(PostCategory.INQUIRY_HELP);
        request.setTitle("Question");
        request.setContent("Details");
        return request;
    }
}
