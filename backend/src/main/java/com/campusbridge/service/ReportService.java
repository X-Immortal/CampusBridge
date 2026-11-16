package com.campusbridge.service;

import com.campusbridge.enums.ReportStatus;
import com.campusbridge.common.PagedResult;
import com.campusbridge.dto.request.CreateReportRequest;
import com.campusbridge.dto.request.ResolveReportRequest;
import com.campusbridge.entity.ModerationRecord;
import com.campusbridge.entity.Report;

public interface ReportService {
    Report createReport(Long reporterId, CreateReportRequest request);

    PagedResult<Report> listReports(
            Long reporterId,
            ReportStatus status,
            Integer page,
            Integer size);

    PagedResult<Report> listReportsForAdmin(
            ReportStatus status,
            Integer page,
            Integer size);

    ModerationRecord resolveReport(
            Long handlerId,
            Long reportId,
            ResolveReportRequest request);
}
