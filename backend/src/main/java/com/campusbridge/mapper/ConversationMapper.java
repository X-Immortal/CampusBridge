package com.campusbridge.mapper;

import com.campusbridge.pojo.Conversation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ConversationMapper {
    int insert(Conversation entity);

    Conversation selectById(Long id);

    List<Conversation> selectAll();

    int updateById(Conversation entity);

    int deleteById(Long id);
}
