package com.campusbridge.mapper;

import com.campusbridge.pojo.Reply;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReplyMapper {
    int insert(Reply entity);

    Reply selectById(Long id);

    List<Reply> selectAll();

    int updateById(Reply entity);

    int deleteById(Long id);
}
