package com.campusbridge.common;

import com.campusbridge.dto.request.ConfirmEmailVerificationRequest;
import com.campusbridge.dto.request.CreateVerificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse body = ErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getDetails()
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(BadCredentialsException ex) {
        ErrorCode code = ErrorCode.AUTH_002_INVALID_CREDENTIALS;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code, code.getDefaultMessage(), null));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex) {
        ErrorCode code = ErrorCode.AUTH_001_UNAUTHORIZED;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code, "账号已被禁用", null));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        ErrorCode code = ErrorCode.COMMON_001_VALIDATION_ERROR;
        if (ex instanceof MethodArgumentNotValidException validationException) {
            Object request = validationException.getBindingResult().getTarget();
            if (request instanceof CreateVerificationRequest || request instanceof ConfirmEmailVerificationRequest) {
                code = ErrorCode.VERIFY_001_INVALID_INPUT;
            }
        }
        ErrorResponse body = ErrorResponse.of(code, code.getDefaultMessage(), detailsOf(ex));
        return ResponseEntity.status(code.getHttpStatus()).body(body);
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
        log.error("Unhandled request failure", ex);
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.COMMON_999_INTERNAL_ERROR,
                ErrorCode.COMMON_999_INTERNAL_ERROR.getDefaultMessage(),
                null
        );
        return ResponseEntity.status(ErrorCode.COMMON_999_INTERNAL_ERROR.getHttpStatus()).body(body);
    }

    private List<String> detailsOf(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validationException) {
            return validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .toList();
        }
        if (ex instanceof HandlerMethodValidationException validationException) {
            return validationException.getAllErrors().stream()
                    .map(org.springframework.context.MessageSourceResolvable::getDefaultMessage)
                    .toList();
        }
        if (!(ex instanceof IllegalArgumentException)
                || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return null;
        }
        return List.of(ex.getMessage());
    }
}
