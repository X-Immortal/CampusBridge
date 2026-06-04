package com.campusbridge.mapper;

import com.campusbridge.pojo.CreditRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CreditRecordMapper {
    int insert(CreditRecord entity);

    CreditRecord selectById(Long id);

    List<CreditRecord> selectAll();

    int updateById(CreditRecord entity);

    int deleteById(Long id);
}
