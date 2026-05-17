package com.campusbridge.mapper;

import com.campusbridge.pojo.Report;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReportMapper {
    int insert(Report entity);

    Report selectById(Long id);

    List<Report> selectAll();

    int updateById(Report entity);

    int deleteById(Long id);
}
