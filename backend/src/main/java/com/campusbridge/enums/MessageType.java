package com.campusbridge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {
    TEXT(0),
    IMAGE(1);

    private final int attachmentCount;
}
