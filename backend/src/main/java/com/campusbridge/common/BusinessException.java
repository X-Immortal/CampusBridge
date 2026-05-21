package com.campusbridge.common;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(final ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
