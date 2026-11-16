package com.campusbridge.mapper;

import com.campusbridge.pojo.CampusVerification;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CampusVerificationMapper {
    int insert(CampusVerification entity);

    CampusVerification selectById(Long id);

    CampusVerification selectLatestByUserId(Long userId);

    List<CampusVerification> selectAll();

    List<CampusVerification> selectByStatus(@Param("status") String status);

    int updateById(CampusVerification entity);

    int updatePending(CampusVerification entity);

    int deletePendingById(Long id);

    int deleteById(Long id);
}
