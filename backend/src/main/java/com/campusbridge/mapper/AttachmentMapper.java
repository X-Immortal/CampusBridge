package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.Attachment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {

    default List<Attachment> selectByOwner(
            String ownerType,
            Long ownerId) {
        return selectList(Wrappers.<Attachment>lambdaQuery()
                .eq(Attachment::getOwnerType, ownerType).eq(Attachment::getOwnerId, ownerId)
                .in(Attachment::getStatus, "ACTIVE", "BOUND")
                .orderByAsc(Attachment::getCreatedAt, Attachment::getId));
    }

    default int bindTemporaryToOwner(Long attachmentId, Long uploaderId, String ownerType, Long ownerId) {
        return update(null, Wrappers.<Attachment>lambdaUpdate()
                .set(Attachment::getOwnerType, ownerType)
                .set(Attachment::getOwnerId, ownerId)
                .set(Attachment::getStatus, "BOUND")
                .eq(Attachment::getId, attachmentId)
                .eq(Attachment::getUploaderId, uploaderId)
                .eq(Attachment::getOwnerType, ownerType)
                .isNull(Attachment::getOwnerId)
                .eq(Attachment::getStatus, "TEMPORARY"));
    }

}
