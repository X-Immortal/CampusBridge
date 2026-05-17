package com.campusbridge.mapper;

import com.campusbridge.pojo.CampusVerification;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CampusVerificationMapper {
    int insert(CampusVerification entity);

    CampusVerification selectById(Long id);

    List<CampusVerification> selectAll();

    int updateById(CampusVerification entity);

    int deleteById(Long id);
}
