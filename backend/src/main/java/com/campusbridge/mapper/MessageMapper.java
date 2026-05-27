package com.campusbridge.mapper;

import com.campusbridge.pojo.Message;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageMapper {
    int insert(Message entity);

    Message selectById(Long id);

    List<Message> selectAll();

    int updateById(Message entity);

    int deleteById(Long id);
}
