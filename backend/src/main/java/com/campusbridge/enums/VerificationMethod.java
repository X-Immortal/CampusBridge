package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationMethod {
    STUDENT_CARD_IMAGE(true, false),
    CAMPUS_EMAIL(false, true),
    MANUAL_REVIEW(true, false);

    private final boolean evidenceRequired;
    private final boolean campusEmailRequired;
}
