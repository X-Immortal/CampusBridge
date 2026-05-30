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

    long countWithFilters(
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    int updateById(Post entity);

    int deleteById(Long id);
}
