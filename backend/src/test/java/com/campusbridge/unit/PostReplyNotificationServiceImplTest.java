package com.campusbridge.unit;

import com.campusbridge.entity.Conversation;
import com.campusbridge.entity.Message;
import com.campusbridge.entity.Post;
import com.campusbridge.entity.Reply;
import com.campusbridge.mapper.ConversationMapper;
import com.campusbridge.mapper.MessageMapper;
import com.campusbridge.mapper.UserMapper;
import com.campusbridge.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostReplyNotificationServiceImplTest {
    private ConversationMapper conversationMapper;
    private MessageMapper messageMapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        messageMapper = mock(MessageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        service = new NotificationServiceImpl(conversationMapper, messageMapper, userMapper);
        when(conversationMapper.insert(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(88L);
            return 1;
        });
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
    }

    @Test
    void topLevelReplyNotifiesPostAuthorAndLinksPost() {
        Post post = post(7L, 10L);
        Reply reply = reply(20L, 7L, 30L, null);

        service.notifyPostReplyCreated(post, reply, null, 30L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getOwnerId());
        assertEquals("SYSTEM", captor.getValue().getType());
        assertEquals("POST", captor.getValue().getRelatedType());
        assertEquals(7L, captor.getValue().getRelatedId());
        assertEquals(0, captor.getValue().getReplyable());
    }

    @Test
    void nestedReplyNotifiesParentReplyAuthor() {
        Post post = post(7L, 10L);
        Reply parent = reply(19L, 7L, 40L, null);
        Reply reply = reply(20L, 7L, 30L, 19L);

        service.notifyPostReplyCreated(post, reply, parent, 30L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertEquals(40L, captor.getValue().getOwnerId());
    }

    @Test
    void selfReplyDoesNotCreateNotification() {
        Post post = post(7L, 30L);
        Reply reply = reply(20L, 7L, 30L, null);

        service.notifyPostReplyCreated(post, reply, null, 30L);

        verify(conversationMapper, never()).insert(any(Conversation.class));
        verify(messageMapper, never()).insert(any(Message.class));
    }

    private Post post(Long id, Long authorId) {
        Post post = new Post();
        post.setId(id);
        post.setAuthorId(authorId);
        post.setTitle("Campus post");
        return post;
    }

    private Reply reply(Long id, Long postId, Long authorId, Long parentReplyId) {
        Reply reply = new Reply();
        reply.setId(id);
        reply.setPostId(postId);
        reply.setAuthorId(authorId);
        reply.setParentReplyId(parentReplyId);
        reply.setContent("A new reply");
        return reply;
    }
}
