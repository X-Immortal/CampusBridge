package com.campusbridge.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTH_001_UNAUTHORIZED("未认证或认证已过期",
            HttpStatus.UNAUTHORIZED),
    AUTH_002_INVALID_CREDENTIALS("用户名或密码错误", HttpStatus.UNAUTHORIZED),
    AUTH_003_TOKEN_EXPIRED("Token 已过期", HttpStatus.UNAUTHORIZED),
    USER_001_NOT_FOUND("用户不存在", HttpStatus.NOT_FOUND),
    USER_002_NOT_VERIFIED("用户未完成校内认证", HttpStatus.FORBIDDEN),
    POST_001_NOT_FOUND("帖子不存在", HttpStatus.NOT_FOUND),
    POST_002_PERMISSION_DENIED("无权限操作该帖子", HttpStatus.FORBIDDEN),
    POST_003_REPLY_NOT_FOUND("回复不存在", HttpStatus.NOT_FOUND),
    TASK_001_NOT_FOUND("任务不存在", HttpStatus.NOT_FOUND),
    TASK_002_INVALID_STATUS_TRANSITION("任务状态流转不合法", HttpStatus.CONFLICT),
    TASK_003_PERMISSION_DENIED("无权限操作该任务", HttpStatus.FORBIDDEN),
    TASK_004_APPLICATION_NOT_FOUND("接单申请不存在", HttpStatus.NOT_FOUND),
    TASK_007_ALREADY_APPLIED("已申请过该任务", HttpStatus.CONFLICT),
    TASK_008_ALREADY_ACCEPTED("任务已选择帮助者", HttpStatus.CONFLICT),
    TASK_006_COMPLETION_PROOF_REQUIRED("需要提交完成凭证",
            HttpStatus.UNPROCESSABLE_ENTITY
    ),
    REVIEW_001_DUPLICATE_REVIEW("不能重复评价", HttpStatus.CONFLICT),
    NOTICE_001_NOT_FOUND("通知不存在", HttpStatus.NOT_FOUND),
    CONV_001_NOT_FOUND("会话不存在", HttpStatus.NOT_FOUND),
    CONV_002_NOT_REPLYABLE("该会话不可回复", HttpStatus.FORBIDDEN),
    CONV_003_BLOCKED("双方存在拉黑关系，无法继续沟通", HttpStatus.FORBIDDEN),
    FILE_001_TOO_LARGE("文件过大", HttpStatus.BAD_REQUEST),
    FILE_002_UNSUPPORTED_TYPE("不支持的文件类型", HttpStatus.BAD_REQUEST),
    FILE_003_NOT_FOUND("文件不存在", HttpStatus.NOT_FOUND),
    FILE_004_INVALID_ATTACHMENT("附件无效或无权使用", HttpStatus.BAD_REQUEST),
    REPORT_001_NOT_FOUND("举报不存在", HttpStatus.NOT_FOUND),
    MOD_001_PERMISSION_DENIED("无管理权限", HttpStatus.FORBIDDEN),
    CONTENT_001_BLOCKED("内容命中平台规则，已拦截",
            HttpStatus.UNPROCESSABLE_ENTITY),
    ATTACH_001_INVALID_INPUT("Uploader or file is invalid", HttpStatus.BAD_REQUEST),
    ATTACH_002_INVALID_OWNER_TYPE("expectedOwnerType is invalid", HttpStatus.BAD_REQUEST),
    ATTACH_004_UPLOAD_FAILED("Upload to OSS failed", HttpStatus.BAD_GATEWAY),
    ATTACH_005_SAVE_FAILED("Failed to save attachment metadata", HttpStatus.INTERNAL_SERVER_ERROR),
    VERIFY_001_INVALID_INPUT("Invalid verification request", HttpStatus.BAD_REQUEST),
    VERIFY_002_ALREADY_PENDING_OR_VERIFIED("Verification already pending or approved", HttpStatus.CONFLICT),
    VERIFY_003_SUBMIT_FAILED("Failed to save verification", HttpStatus.INTERNAL_SERVER_ERROR),
    VERIFY_004_NOT_FOUND("Verification record not found", HttpStatus.NOT_FOUND),
    VERIFY_005_EMAIL_SEND_FAILED("Failed to send verification email", HttpStatus.BAD_GATEWAY),
    VERIFY_006_EMAIL_CODE_NOT_PENDING("No pending email verification", HttpStatus.CONFLICT),
    VERIFY_007_EMAIL_CODE_EXPIRED("Verification code expired", HttpStatus.BAD_REQUEST),
    VERIFY_008_EMAIL_CODE_MISMATCH("Verification code is incorrect", HttpStatus.BAD_REQUEST),
    VERIFY_009_CANNOT_CANCEL("Only pending verification can be cancelled", HttpStatus.CONFLICT),
    COMMON_001_VALIDATION_ERROR("请求参数错误", HttpStatus.BAD_REQUEST),
    COMMON_002_RATE_LIMITED("请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    COMMON_003_CONFLICT("资源状态冲突", HttpStatus.CONFLICT),
    COMMON_999_INTERNAL_ERROR("服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    public String getCode() {
        return name();
    }

    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
