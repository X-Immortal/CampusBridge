package com.campusbridge.service.impl;

import com.campusbridge.service.VerificationOcrClient;
import org.springframework.stereotype.Service;

@Service
public class StubVerificationOcrClient implements VerificationOcrClient {

    @Override
    public OcrExtractResult extractStudentCard(String evidenceUrl) {
        return OcrExtractResult.unavailable();
    }
}
