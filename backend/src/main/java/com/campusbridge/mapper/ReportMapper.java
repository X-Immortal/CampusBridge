package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.entity.Report;
import com.campusbridge.enums.ReportStatus;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    default int updateStatusById(Long id, ReportStatus status) {
        return update(null, Wrappers.<Report>lambdaUpdate()
                .set(Report::getStatus, status)
                .eq(Report::getId, id)
                .in(Report::getStatus, ReportStatus.PENDING, ReportStatus.PROCESSING));
    }

}
