package com.campusbridge.common;

import lombok.Getter;

public enum ErrorCode {
    PARAM_INVALID("PARAM_INVALID", "请求参数不合法"),
    UNAUTHORIZED("UNAUTHORIZED", "用户未登录或登录已失效"),
    FORBIDDEN("FORBIDDEN", "无权限执行该操作"),
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    STATE_CONFLICT("STATE_CONFLICT", "当前状态不允许执行该操作"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "资源已存在"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    @Getter
    private final String code;
    @Getter
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

}
