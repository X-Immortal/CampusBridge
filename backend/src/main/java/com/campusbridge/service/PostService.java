package com.campusbridge.service;

import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.view.MyReplyPostView;
import com.campusbridge.dto.view.PostDetailView;
import com.campusbridge.dto.view.PostView;

public interface PostService {
    PostView createPost(Long userId, CreatePostRequest request);

    PagedResult<PostView> listPosts(
            String keyword, String category, String status, Integer page, Integer size);

    PagedResult<MyReplyPostView> listPostsRepliedByUser(
            Long userId, String keyword, String category, String status, Integer page, Integer size);

    PostDetailView getPostDetail(Long postId, Long currentUserId);

    PostView updatePost(Long userId, Long postId, UpdatePostRequest request);

    void deletePost(Long userId, Long postId);
}
