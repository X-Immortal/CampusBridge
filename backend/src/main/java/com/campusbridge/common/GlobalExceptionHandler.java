package com.campusbridge.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
            MethodArgumentNotValidException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.COMMON_001_VALIDATION_ERROR,
                ErrorCode.COMMON_001_VALIDATION_ERROR.getDefaultMessage(),
                detailsOf(ex)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleOversizedUpload(MaxUploadSizeExceededException ex) {
        ErrorCode code = ErrorCode.FILE_001_TOO_LARGE;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code, code.getDefaultMessage(), null));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        ErrorCode code = ErrorCode.COMMON_003_CONFLICT;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code, code.getDefaultMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        LOGGER.error("Unhandled request failure", ex);
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
        if (!(ex instanceof IllegalArgumentException)
                || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return null;
        }
        return List.of(ex.getMessage());
    }
}
