package com.campusbridge.mapper;

import com.campusbridge.pojo.PostInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostInteractionMapper {
    int insert(PostInteraction entity);

    PostInteraction selectById(Long id);

    PostInteraction selectByUserTargetType(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("type") String type);

    List<PostInteraction> selectAll();

    int updateById(PostInteraction entity);

    int deleteByUserTargetType(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("type") String type);

    List<Long> selectTargetIdsByUserAndType(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("type") String type);

    int deleteById(Long id);
}
