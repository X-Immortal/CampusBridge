package com.campusbridge.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private List<String> details;
    private Meta meta;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String requestId;
        private LocalDateTime timestamp;
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<String> details) {
        Meta meta = new Meta(UUID.randomUUID().toString(), LocalDateTime.now());
        return new ErrorResponse(errorCode.getCode(), message, details, meta);
    }
}
