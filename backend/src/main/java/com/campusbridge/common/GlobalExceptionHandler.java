package com.campusbridge.common;

import com.campusbridge.pojo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleBusinessException(BusinessException ex) {
        return Result.error(
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                ex.getData()
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class
    })
    @ResponseStatus(HttpStatus.OK)
    public Result handleBadRequest(Exception ex) {
        return Result.error(
                ErrorCode.PARAM_INVALID.getCode(),
                ErrorCode.PARAM_INVALID.getDefaultMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result handleUnexpectedException(Exception ex) {
        return Result.error(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage()
        );
    }
}
