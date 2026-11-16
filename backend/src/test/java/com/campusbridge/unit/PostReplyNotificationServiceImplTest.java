package com.campusbridge.unit;

import com.campusbridge.enums.ReplyStatus;
import com.campusbridge.dto.request.CreateReplyRequest;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.enums.PostStatus;
import com.campusbridge.enums.NotificationType;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.service.NotificationService;
import com.campusbridge.service.ReplyNotificationService;
import com.campusbridge.service.impl.ReplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostReplyNotificationServiceImplTest {
    private final PostMapper posts = mock(PostMapper.class);
    private final ReplyMapper replies = mock(ReplyMapper.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReplyServiceImpl service = new ReplyServiceImpl(replies, posts, new ReplyNotificationService(notifications));

    @BeforeEach
    void setUp() {
        Post post = new Post();
        post.setId(7L);
        post.setAuthorId(10L);
        post.setTitle("Campus post");
        post.setStatus(PostStatus.PUBLISHED);
        when(posts.selectById(7L)).thenReturn(post);
        when(posts.incrementReplyCount(7L)).thenReturn(1);
        when(replies.insert(any(Reply.class))).thenReturn(1);
    }

    @Test
    void topLevelReplyNotifiesPostAuthorAndLinksPost() {
        service.createReply(30L, 7L, request(null));
        verify(notifications).create(eq(10L), eq(NotificationType.POST_REPLY),
                eq("帖子收到新回复"), contains("A new reply"), eq(7L));
    }

    @Test
    void nestedReplyNotifiesParentReplyAuthor() {
        Reply parent = new Reply();
        parent.setId(19L);
        parent.setPostId(7L);
        parent.setAuthorId(40L);
        parent.setStatus(ReplyStatus.PUBLISHED);
        when(replies.selectById(19L)).thenReturn(parent);
        service.createReply(30L, 7L, request(19L));
        verify(notifications).create(eq(40L), eq(NotificationType.POST_REPLY),
                eq("你的回复收到新回复"), anyString(), eq(7L));
    }

    @Test
    void selfReplyDoesNotCreateNotification() {
        service.createReply(10L, 7L, request(null));
        verifyNoInteractions(notifications);
    }

    private CreateReplyRequest request(Long parentId) {
        CreateReplyRequest request = new CreateReplyRequest();
        request.setContent("A new reply");
        request.setParentReplyId(parentId);
        return request;
    }
}
