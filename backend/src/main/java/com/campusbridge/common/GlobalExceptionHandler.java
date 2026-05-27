package com.campusbridge.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse body = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getDetails()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.COMMON_001_VALIDATION_ERROR,
                ErrorCode.COMMON_001_VALIDATION_ERROR.getDefaultMessage(),
                detailsOf(ex)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.COMMON_999_INTERNAL_ERROR,
                ErrorCode.COMMON_999_INTERNAL_ERROR.getDefaultMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private List<String> detailsOf(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validationException) {
            return validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
        }
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return null;
        }
        return List.of(ex.getMessage());
    }
}
