package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusbridge.dto.view.ReplyView;
import com.campusbridge.entity.Reply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplyMapper extends BaseMapper<Reply> {

    List<ReplyView> selectByPostId(
            @Param("postId") Long postId,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    List<ReplyView> selectByAuthorAndPostIds(
            @Param("authorId") Long authorId,
            @Param("postIds") List<Long> postIds);

}
