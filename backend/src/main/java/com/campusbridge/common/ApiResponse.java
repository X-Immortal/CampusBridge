package com.campusbridge.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;
    private ResponseMeta meta;

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> result = new ApiResponse<>();
        result.code = "SUCCESS";
        result.message = "OK";
        result.data = data;
        result.meta = new ResponseMeta(UUID.randomUUID().toString(), LocalDateTime.now());
        return result;
    }

}
