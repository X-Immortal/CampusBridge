package com.campusbridge.mapper;

import com.campusbridge.pojo.ModerationRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModerationRecordMapper {
    int insert(ModerationRecord entity);

    ModerationRecord selectById(Long id);

    List<ModerationRecord> selectAll();

    int updateById(ModerationRecord entity);

    int deleteById(Long id);
}
