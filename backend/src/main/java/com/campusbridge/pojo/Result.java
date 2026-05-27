package com.campusbridge.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private String code;
    private String message;
    private Object data;
    private Meta meta;

    @Data
    public static class Meta {
        private String requestId;
        private LocalDateTime timestamp;

    }

    public static Result success() {
        return success(null);
    }

    public static Result success(Object data) {
        Result result = new Result();
        result.code = "SUCCESS";
        result.message = "OK";
        result.data = data;
        Meta m = new Meta();
        m.requestId = UUID.randomUUID().toString();
        m.timestamp = LocalDateTime.now();
        result.meta = m;
        return result;
    }

    public static Result error(String code, String message) {
        return error(code, message, null);
    }

    public static Result error(String code, String message, Object data) {
        Result result = new Result();
        result.code = code;
        result.message = message;
        result.data = data;
        Meta m = new Meta();
        m.requestId = UUID.randomUUID().toString();
        m.timestamp = LocalDateTime.now();
        result.meta = m;
        return result;
    }
}
