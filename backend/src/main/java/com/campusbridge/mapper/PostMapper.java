package com.campusbridge.mapper;

import com.campusbridge.pojo.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {
    int insert(Post entity);

    Post selectById(Long id);

    List<Post> selectAll();

    List<Post> selectWithFilters(
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    List<Post> selectRepliedByUser(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countWithFilters(
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    long countRepliedByUser(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    int updateById(Post entity);

    int updateContent(@Param("post") Post entity, @Param("expectedStatus") String expectedStatus);

    int incrementReplyCount(@Param("id") Long id);

    int adjustInteractionCount(@Param("id") Long id, @Param("type") String type, @Param("delta") int delta);

    int updateAcceptedReply(@Param("id") Long id, @Param("replyId") Long replyId);

    int deleteById(Long id);

    List<Post> selectHotPosts(@Param("size") Integer size);

    List<Post> selectByIds(@Param("ids") List<Long> ids);

    List<Post> selectAllPublishedPosts();
}
