package com.campusbridge.mapper;

import com.campusbridge.pojo.Reply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplyMapper {
    int insert(Reply entity);

    Reply selectById(Long id);

    List<Reply> selectAll();

    List<Reply> selectByPostId(
            @Param("postId") Long postId,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    List<Reply> selectByAuthorAndPostIds(
            @Param("authorId") Long authorId,
            @Param("postIds") List<Long> postIds);

    long countByPostId(@Param("postId") Long postId);

    int updateById(Reply entity);

    int deleteById(Long id);
}
