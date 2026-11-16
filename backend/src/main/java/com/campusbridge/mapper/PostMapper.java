package com.campusbridge.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.pojo.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    PostView selectDetailById(Long id);

    Page<PostView> selectWithFilters(
            Page<PostView> page,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    Page<PostView> selectRepliedByUser(
            Page<PostView> page,
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    default int updateContent(Post entity, String expectedStatus) {
        return update(null, Wrappers.<Post>lambdaUpdate()
                .set(Post::getTitle, entity.getTitle())
                .set(Post::getContent, entity.getContent())
                .set(Post::getStatus, entity.getStatus())
                .set(Post::getUpdatedAt, entity.getUpdatedAt())
                .eq(Post::getId, entity.getId())
                .eq(Post::getAuthorId, entity.getAuthorId())
                .eq(Post::getStatus, expectedStatus));
    }

    default int incrementReplyCount(Long id) {
        return update(null, Wrappers.<Post>lambdaUpdate()
                .setIncrBy(Post::getReplyCount, 1)
                .eq(Post::getId, id)
                .eq(Post::getStatus, "PUBLISHED"));
    }

    int adjustInteractionCount(@Param("id") Long id, @Param("type") String type, @Param("delta") int delta);

    default int updateAcceptedReply(Long id, Long replyId) {
        return update(null, Wrappers.<Post>lambdaUpdate()
                .set(Post::getAcceptedReplyId, replyId)
                .eq(Post::getId, id)
                .in(Post::getStatus, "PUBLISHED", "CLOSED"));
    }

    default int markDeletedById(Long id) {
        return update(null, Wrappers.<Post>lambdaUpdate()
                .set(Post::getStatus, "DELETED")
                .setSql("updated_at = CURRENT_TIMESTAMP")
                .eq(Post::getId, id)
                .ne(Post::getStatus, "DELETED"));
    }

    List<PostView> selectHotPosts(@Param("size") Integer size);

    List<PostView> selectPublishedByIds(@Param("ids") List<Long> ids);

    List<PostView> selectAllPublishedPosts();
}
