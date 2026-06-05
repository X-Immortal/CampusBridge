package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.pojo.Post;

public interface PostService {
    Post createPost(Long userId, RequestDtos.CreatePostRequest request);

    ResponseDtos.PagedResult<Post> listPosts(
            String keyword, String category, String status, Integer page, Integer size);

    ViewDtos.PostDetailView getPostDetail(Long postId, Long currentUserId);

    Post updatePost(Long userId, Long postId, RequestDtos.UpdatePostRequest request);

    void deletePost(Long userId, Long postId);
}
