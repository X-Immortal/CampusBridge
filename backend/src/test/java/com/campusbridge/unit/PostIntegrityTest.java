package com.campusbridge.unit;

import com.campusbridge.common.BusinessException;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.mapper.PostInteractionMapper;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.pojo.Post;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.pojo.PostInteraction;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.NotificationService;
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
        replyService = new ReplyServiceImpl(replies, posts, mock(NotificationService.class));
        interactionService = new InteractionServiceImpl(interactions, posts, replies);
    }

    @Test
    void blankPostCannotReachPersistence() {
        RequestDtos.CreatePostRequest request = request();
        request.setTitle(" ");
        assertThatThrownBy(() -> service.createPost(1L, request)).isInstanceOf(BusinessException.class);
        verify(posts, never()).insert(any(Post.class));
    }

    @Test
    void editingCannotBypassPublishModeration() {
        when(posts.selectById(10L)).thenReturn(post("PUBLISHED"));
        RequestDtos.UpdatePostRequest request = new RequestDtos.UpdatePostRequest();
        request.setContent("代考");
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        verify(posts, never()).updateContent(any(), any());
    }

    @Test
    void authorCannotPublishModeratedPostOrSetModerationStatus() {
        when(posts.selectById(10L)).thenReturn(post("HIDDEN"), post("PUBLISHED"));
        RequestDtos.UpdatePostRequest request = new RequestDtos.UpdatePostRequest();
        request.setStatus(ApiEnums.PostStatus.PUBLISHED);
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        request.setStatus(ApiEnums.PostStatus.HIDDEN);
        assertThatThrownBy(() -> service.updatePost(1L, 10L, request)).isInstanceOf(BusinessException.class);
        verify(posts, never()).updateContent(any(), any());
    }

    @Test
    void anonymousOrOtherUserCannotReadHiddenPost() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post("HIDDEN")));
        assertThatThrownBy(() -> service.getPostDetail(10L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getPostDetail(10L, 2L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void ownerCanReadHiddenPost() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post("HIDDEN")));
        assertThat(service.getPostDetail(10L, 1L).getPost().getId()).isEqualTo(10L);
    }

    @Test
    void deletedPostIsNotReadableEvenByAuthor() {
        when(posts.selectDetailById(10L)).thenReturn(PostView.from(post("DELETED")));
        assertThatThrownBy(() -> service.getPostDetail(10L, 1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void postAttachmentUsesAtomicTemporaryOwnershipBinding() {
        Attachment attachment = new Attachment();
        attachment.setId(3L);
        attachment.setUploaderId(1L);
        attachment.setOwnerType("POST");
        attachment.setStatus("TEMPORARY");
        when(attachments.selectById(3L)).thenReturn(attachment);
        when(attachments.bindTemporaryToOwner(3L, 1L, "POST", 10L)).thenReturn(1);
        when(posts.insert(any(Post.class))).thenAnswer(invocation -> {
            ((Post) invocation.getArgument(0)).setId(10L);
            return 1;
        });
        RequestDtos.CreatePostRequest request = request();
        request.setAttachmentIds(List.of(3L));

        service.createPost(1L, request);

        verify(attachments).bindTemporaryToOwner(3L, 1L, "POST", 10L);
        verify(attachments, never()).updateById(any(Attachment.class));
    }

    @Test
    void replyUsesAtomicCountAndRejectsClosedPosts() {
        when(posts.selectById(10L)).thenReturn(post("PUBLISHED"), post("CLOSED"));
        when(replies.insert(any(Reply.class))).thenReturn(1);
        when(posts.incrementReplyCount(10L)).thenReturn(1);
        RequestDtos.CreateReplyRequest request = new RequestDtos.CreateReplyRequest();
        request.setContent("Reply");
        replyService.createReply(2L, 10L, request);
        verify(posts).incrementReplyCount(10L);
        verify(posts, never()).updateById(any(Post.class));
        assertThatThrownBy(() -> replyService.createReply(2L, 10L, request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptingReplyDoesNotInsertUnsupportedInteractionType() {
        when(posts.selectById(10L)).thenReturn(post("PUBLISHED"));
        Reply reply = new Reply();
        reply.setId(20L);
        reply.setPostId(10L);
        reply.setStatus("PUBLISHED");
        when(replies.selectById(20L)).thenReturn(reply);
        when(posts.updateAcceptedReply(10L, 20L)).thenReturn(1);

        assertThat(interactionService.acceptReply(1L, 10L, 20L).getType()).isEqualTo("ACCEPT_REPLY");

        verify(posts).updateAcceptedReply(10L, 20L);
        verify(interactions, never()).insert(any(PostInteraction.class));
    }

    private Post post(String status) {
        Post post = new Post();
        post.setId(10L);
        post.setAuthorId(1L);
        post.setTitle("Question");
        post.setContent("Details");
        post.setCategory("INQUIRY_HELP");
        post.setStatus(status);
        return post;
    }

    private RequestDtos.CreatePostRequest request() {
        RequestDtos.CreatePostRequest request = new RequestDtos.CreatePostRequest();
        request.setCategory(ApiEnums.PostCategory.INQUIRY_HELP);
        request.setTitle("Question");
        request.setContent("Details");
        return request;
    }
}
