package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.mapper.PostMapper;
import com.campusbridge.mapper.ReplyMapper;
import com.campusbridge.pojo.Post;
import com.campusbridge.pojo.Reply;
import com.campusbridge.service.ContentModerationService;
import com.campusbridge.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Transactional
@Service
public class PostServiceImpl implements PostService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final PostMapper postMapper;
    private final ReplyMapper replyMapper;
    private final ContentModerationService contentModerationService;

    public PostServiceImpl(
            PostMapper postMapper,
            ReplyMapper replyMapper,
            ContentModerationService contentModerationService) {
        this.postMapper = postMapper;
        this.replyMapper = replyMapper;
        this.contentModerationService = contentModerationService;
    }

    @Override
    public Post createPost(Long userId, RequestDtos.CreatePostRequest request) {
        contentModerationService.assertPostPublishAllowed(userId, request);

        LocalDateTime now = LocalDateTime.now();
        Post post = new Post();
        post.setAuthorId(userId);
        post.setCategory(request.getCategory().name());
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setStatus("PUBLISHED");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setMarkCount(0);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        if (postMapper.insert(post) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to create post");
        }
        return post;
    }

    @Override
    public ResponseDtos.PagedResult<Post> listPosts(
            String keyword, String category, String status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (safePage - 1) * safeSize;

        List<Post> posts = postMapper.selectWithFilters(category, status, keyword, offset, safeSize);
        long total = postMapper.countWithFilters(category, status, keyword);
        return new ResponseDtos.PagedResult<>(posts, safePage, safeSize, total);
    }

    @Override
    public ViewDtos.PostDetailView getPostDetail(Long postId) {
        Post post = requirePostExists(postId);
        List<Reply> replies = replyMapper.selectByPostId(postId, 0, 50);
        return new ViewDtos.PostDetailView(
                post, replies, false, false, post.getAcceptedReplyId());
    }

    @Override
    public Post updatePost(Long userId, Long postId, RequestDtos.UpdatePostRequest request) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
        }
        if (request.getStatus() != null) {
            post.setStatus(request.getStatus().name());
        }
        post.setUpdatedAt(LocalDateTime.now());

        if (postMapper.updateById(post) != 1) {
            throw new BusinessException(ErrorCode.COMMON_999_INTERNAL_ERROR,
                    "Failed to update post");
        }
        return post;
    }

    @Override
    public void deletePost(Long userId, Long postId) {
        Post post = requirePostExists(postId);
        if (!userId.equals(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_002_PERMISSION_DENIED);
        }
        postMapper.deleteById(postId);
    }

    private Post requirePostExists(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_001_NOT_FOUND);
        }
        return post;
    }
}
