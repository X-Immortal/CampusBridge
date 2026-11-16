package com.campusbridge.controller;

import com.campusbridge.common.ApiResponse;
import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.User;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.utils.AuthContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AuthContextUtil authContextUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Attachment> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long fallbackUserId,
            @RequestHeader(value = "X-Expected-Owner-Type", required = false) String expectedOwnerType,
            @RequestHeader(value = "X-Attachment-Type", required = false) String type) {
        Long currentUserId = authContextUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_001_UNAUTHORIZED);
        }
        return ApiResponse.success(attachmentService.uploadAttachment(currentUserId, file, expectedOwnerType, type));
    }
}
