package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.dto.view.PostView;

public interface PostService {
    PostView createPost(Long userId, RequestDtos.CreatePostRequest request);

    ResponseDtos.PagedResult<PostView> listPosts(
            String keyword, String category, String status, Integer page, Integer size);

    ResponseDtos.PagedResult<ViewDtos.MyReplyPostView> listPostsRepliedByUser(
            Long userId, String keyword, String category, String status, Integer page, Integer size);

    ViewDtos.PostDetailView getPostDetail(Long postId, Long currentUserId);

    PostView updatePost(Long userId, Long postId, RequestDtos.UpdatePostRequest request);

    void deletePost(Long userId, Long postId);
}
