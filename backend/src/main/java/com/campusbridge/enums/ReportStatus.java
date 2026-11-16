package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportStatus {
    PENDING(false),
    PROCESSING(false),
    APPROVED(true),
    REJECTED(true),
    RESOLVED(true),
    PUNISHED(true);

    private final boolean terminal;
}
