package com.campusbridge.mapper;

import com.campusbridge.pojo.Post;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostMapper {
    int insert(Post entity);

    Post selectById(Long id);

    List<Post> selectAll();

    int updateById(Post entity);

    int deleteById(Long id);
}
