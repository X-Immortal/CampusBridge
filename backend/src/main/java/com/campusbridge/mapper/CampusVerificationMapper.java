package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.CampusVerification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CampusVerificationMapper extends BaseMapper<CampusVerification> {

    default CampusVerification selectLatestByUserId(Long userId) {
        return selectOne(Wrappers.<CampusVerification>lambdaQuery()
                .eq(CampusVerification::getUserId, userId)
                .orderByDesc(CampusVerification::getCreatedAt, CampusVerification::getId).last("LIMIT 1"));
    }

    default int updatePending(CampusVerification entity) {
        return update(entity, Wrappers.<CampusVerification>lambdaUpdate()
                .eq(CampusVerification::getId, entity.getId())
                .eq(CampusVerification::getStatus, "PENDING"));
    }

    default int deletePendingById(Long id) {
        return delete(Wrappers.<CampusVerification>lambdaQuery()
                .eq(CampusVerification::getId, id)
                .eq(CampusVerification::getStatus, "PENDING"));
    }

}
