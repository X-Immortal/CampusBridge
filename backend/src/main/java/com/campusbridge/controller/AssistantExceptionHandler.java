package com.campusbridge.controller;

import com.campusbridge.agent.AssistantException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Agent 层自己的异常出口：错误码 + 为什么，别让前端只看到 500。 */
@RestControllerAdvice(assignableTypes = AssistantController.class)
public class AssistantExceptionHandler {

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<Map<String, Object>> handle(AssistantException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.code());
        body.put("message", ex.getMessage());
        body.put("status", ex.status());
        return ResponseEntity.status(HttpStatus.valueOf(ex.status())).body(body);
    }
}
