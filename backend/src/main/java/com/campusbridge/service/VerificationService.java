package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CampusVerification;

public interface VerificationService {
    Object submit(Long userId, RequestDtos.CreateVerificationRequest request);

    CampusVerification getMyLatest(Long userId);
}
