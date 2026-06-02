package com.campusbridge.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_001_UNAUTHORIZED(
            "AUTH_001_UNAUTHORIZED",
            "未认证或认证已过期",
            HttpStatus.UNAUTHORIZED),
    AUTH_002_INVALID_CREDENTIALS("AUTH_002_INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    AUTH_003_TOKEN_EXPIRED("AUTH_003_TOKEN_EXPIRED", "Token 已过期", HttpStatus.UNAUTHORIZED),
    USER_001_NOT_FOUND("USER_001_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND),
    USER_002_NOT_VERIFIED("USER_002_NOT_VERIFIED", "用户未完成校内认证", HttpStatus.FORBIDDEN),
    POST_001_NOT_FOUND("POST_001_NOT_FOUND", "帖子不存在", HttpStatus.NOT_FOUND),
    POST_002_PERMISSION_DENIED("POST_002_PERMISSION_DENIED", "无权限操作该帖子", HttpStatus.FORBIDDEN),
    POST_003_REPLY_NOT_FOUND("POST_003_REPLY_NOT_FOUND", "回复不存在", HttpStatus.NOT_FOUND),
    TASK_001_NOT_FOUND("TASK_001_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND),
    TASK_002_INVALID_STATUS_TRANSITION("TASK_002_INVALID_STATUS_TRANSITION", "任务状态流转不合法", HttpStatus.CONFLICT),
    TASK_003_PERMISSION_DENIED("TASK_003_PERMISSION_DENIED", "无权限操作该任务", HttpStatus.FORBIDDEN),
    TASK_004_APPLICATION_NOT_FOUND("TASK_004_APPLICATION_NOT_FOUND", "接单申请不存在", HttpStatus.NOT_FOUND),
    TASK_007_ALREADY_APPLIED("TASK_007_ALREADY_APPLIED", "已申请过该任务", HttpStatus.CONFLICT),
    TASK_008_ALREADY_ACCEPTED("TASK_008_ALREADY_ACCEPTED", "任务已选择帮助者", HttpStatus.CONFLICT),
    TASK_006_COMPLETION_PROOF_REQUIRED(
            "TASK_006_COMPLETION_PROOF_REQUIRED",
            "需要提交完成凭证",
            HttpStatus.UNPROCESSABLE_ENTITY
    ),
    REVIEW_001_DUPLICATE_REVIEW("REVIEW_001_DUPLICATE_REVIEW", "不能重复评价", HttpStatus.CONFLICT),
    CONV_001_NOT_FOUND("CONV_001_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND),
    CONV_002_NOT_REPLYABLE("CONV_002_NOT_REPLYABLE", "该会话不可回复", HttpStatus.FORBIDDEN),
    FILE_001_TOO_LARGE("FILE_001_TOO_LARGE", "文件过大", HttpStatus.BAD_REQUEST),
    FILE_002_UNSUPPORTED_TYPE("FILE_002_UNSUPPORTED_TYPE", "不支持的文件类型", HttpStatus.BAD_REQUEST),
    FILE_003_NOT_FOUND("FILE_003_NOT_FOUND", "文件不存在", HttpStatus.NOT_FOUND),
    REPORT_001_NOT_FOUND("REPORT_001_NOT_FOUND", "举报不存在", HttpStatus.NOT_FOUND),
    MOD_001_PERMISSION_DENIED("MOD_001_PERMISSION_DENIED", "无管理权限", HttpStatus.FORBIDDEN),
    CONTENT_001_BLOCKED(
            "CONTENT_001_BLOCKED",
            "内容命中平台规则，已拦截",
            HttpStatus.UNPROCESSABLE_ENTITY),
    COMMON_001_VALIDATION_ERROR("COMMON_001_VALIDATION_ERROR", "请求参数错误", HttpStatus.BAD_REQUEST),
    COMMON_002_RATE_LIMITED("COMMON_002_RATE_LIMITED", "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    COMMON_003_CONFLICT("COMMON_003_CONFLICT", "资源状态冲突", HttpStatus.CONFLICT),
    COMMON_999_INTERNAL_ERROR("COMMON_999_INTERNAL_ERROR", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    @Getter
    private final String code;
    @Getter
    private final String defaultMessage;
    @Getter
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

}
