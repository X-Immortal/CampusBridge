package com.campusbridge.service;

import com.campusbridge.dto.request.CreateTaskApplicationRequest;
import com.campusbridge.dto.view.MyTaskApplicationView;
import com.campusbridge.dto.view.TaskApplicationCandidateView;
import com.campusbridge.entity.TaskApplication;

import java.util.List;

public interface TaskApplicationService {
    TaskApplication createApplication(
            Long applicantId,
            Long taskId,
            CreateTaskApplicationRequest request);

    List<TaskApplicationCandidateView> listApplications(
            Long requesterId,
            Long taskId);

    List<MyTaskApplicationView> listMyApplications(Long applicantId);

    TaskApplication withdrawApplication(Long applicantId, Long applicationId);

    TaskApplication acceptApplication(
            Long requesterId,
            Long taskId,
            Long applicationId);
}
