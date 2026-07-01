package com.campusbridge.mapper;

import com.campusbridge.pojo.Attachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachmentMapper {
    int insert(Attachment entity);

    Attachment selectById(Long id);

    List<Attachment> selectAll();

    List<Attachment> selectByOwner(
            @Param("ownerType") String ownerType,
            @Param("ownerId") Long ownerId);

    int updateById(Attachment entity);

    int deleteById(Long id);
}
