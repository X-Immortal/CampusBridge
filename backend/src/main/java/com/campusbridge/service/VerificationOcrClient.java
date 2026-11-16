package com.campusbridge.service;

import com.campusbridge.dto.response.OcrExtractResult;

public interface VerificationOcrClient {
    OcrExtractResult extractStudentCard(String evidenceUrl);
}
