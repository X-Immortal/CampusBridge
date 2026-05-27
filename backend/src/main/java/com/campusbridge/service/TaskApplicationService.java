package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.view.ViewDtos;
import com.campusbridge.pojo.TaskApplication;

import java.util.List;

public interface TaskApplicationService {
    TaskApplication createApplication(
            Long applicantId,
            Long taskId,
            RequestDtos.CreateTaskApplicationRequest request);

    List<ViewDtos.TaskApplicationCandidateView> listApplications(
            Long requesterId,
            Long taskId);

    TaskApplication acceptApplication(
            Long requesterId,
            Long taskId,
            Long applicationId);
}
