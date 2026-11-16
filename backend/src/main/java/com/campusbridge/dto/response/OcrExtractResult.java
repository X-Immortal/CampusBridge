package com.campusbridge.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class OcrExtractResult {
    private final boolean available;
    private final String realName;
    private final String studentNumber;
    private final String school;

    public static OcrExtractResult unavailable() {
        return new OcrExtractResult(false, null, null, null);
    }

    public static OcrExtractResult of(String realName, String studentNumber, String school) {
        return new OcrExtractResult(true, realName, studentNumber, school);
    }
}
