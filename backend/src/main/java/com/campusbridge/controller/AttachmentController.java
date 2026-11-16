package com.campusbridge.controller;

import com.campusbridge.pojo.Result;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.utils.AuthContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AuthContextUtil authContextUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long fallbackUserId,
            @RequestHeader(value = "X-Expected-Owner-Type", required = false) String expectedOwnerType,
            @RequestHeader(value = "X-Attachment-Type", required = false) String type) {
        Long currentUserId = authContextUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
        Object result = attachmentService.uploadAttachment(currentUserId, file, expectedOwnerType, type);
        if (result == null) {
            return Result.error("ATTACH_001_INVALID_INPUT", "Uploader or file is invalid");
        }
        if (result instanceof Long code) {
            if (code == -2L) {
                return Result.error("ATTACH_002_INVALID_OWNER_TYPE", "expectedOwnerType is invalid");
            }
            if (code == -3L) {
                return Result.error("ATTACH_003_INVALID_TYPE", "Attachment type is invalid");
            }
            if (code == -4L) {
                return Result.error("ATTACH_004_UPLOAD_FAILED", "Upload to OSS failed");
            }
            return Result.error("ATTACH_005_SAVE_FAILED", "Failed to save attachment metadata");
        }
        return Result.success(result);
    }
}
