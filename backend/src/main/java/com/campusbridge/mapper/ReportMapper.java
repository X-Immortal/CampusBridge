package com.campusbridge.mapper;

import com.campusbridge.pojo.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {
    int insert(Report entity);

    Report selectById(Long id);

    List<Report> selectAll();

    List<Report> selectByReporterId(
            @Param("reporterId") Long reporterId,
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countByReporterId(
            @Param("reporterId") Long reporterId,
            @Param("status") String status);

    List<Report> selectForAdmin(
            @Param("status") String status,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    long countForAdmin(@Param("status") String status);

    int updateStatusById(
            @Param("id") Long id,
            @Param("status") String status);

    int updateById(Report entity);

    int deleteById(Long id);
}
