package com.campusbridge.mapper;

import com.campusbridge.pojo.PostInteraction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostInteractionMapper {
    int insert(PostInteraction entity);

    PostInteraction selectById(Long id);

    List<PostInteraction> selectAll();

    int updateById(PostInteraction entity);

    int deleteById(Long id);
}
