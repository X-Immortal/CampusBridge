package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.response.ResponseDtos;
import com.campusbridge.pojo.ModerationRecord;
import com.campusbridge.pojo.Report;

public interface ReportService {
    Report createReport(Long reporterId, RequestDtos.CreateReportRequest request);

    ResponseDtos.PagedResult<Report> listReports(
            Long reporterId,
            String status,
            Integer page,
            Integer size);

    ModerationRecord resolveReport(
            Long handlerId,
            Long reportId,
            RequestDtos.ResolveReportRequest request);
}
