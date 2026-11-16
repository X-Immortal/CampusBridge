package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Attachment;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
import com.campusbridge.enums.AttachmentType;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.utils.AliOSSUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final AliOSSUtils aliOSSUtils;

    @Override
    public Attachment uploadAttachment(Long uploaderId, MultipartFile file, String expectedOwnerType, String type) {
        if (uploaderId == null || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTACH_001_INVALID_INPUT);
        }

        AttachmentOwnerType ownerTypeEnum = parseOwnerType(expectedOwnerType);
        if (StringUtils.hasText(expectedOwnerType) && ownerTypeEnum == null) {
            throw new BusinessException(ErrorCode.ATTACH_002_INVALID_OWNER_TYPE);
        }

        AttachmentType attachmentType = parseAttachmentType(type);
        if (StringUtils.hasText(type) && attachmentType == null) {
            throw new BusinessException(ErrorCode.ATTACH_003_INVALID_TYPE);
        }
        if (attachmentType == null) {
            attachmentType = inferAttachmentType(file.getContentType());
        }
        if (attachmentType == null) {
            throw new BusinessException(ErrorCode.ATTACH_003_INVALID_TYPE);
        }

        String fileUrl;
        try {
            fileUrl = aliOSSUtils.upload(file);
        } catch (Exception e) {
            log.error("Attachment upload failed: uploaderId={}", uploaderId, e);
            throw new BusinessException(ErrorCode.ATTACH_004_UPLOAD_FAILED);
        }

        Attachment entity = new Attachment();
        entity.setUploaderId(uploaderId);
        entity.setOwnerType(ownerTypeEnum == null ? null : ownerTypeEnum.name());
        entity.setOwnerId(null);
        entity.setType(attachmentType.name());
        entity.setFileName(file.getOriginalFilename());
        entity.setFileUrl(fileUrl);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        entity.setStatus(AttachmentStatus.TEMPORARY.name());
        entity.setCreatedAt(LocalDateTime.now());

        int affected = attachmentMapper.insert(entity);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.ATTACH_005_SAVE_FAILED);
        }
        return entity;
    }

    private AttachmentOwnerType parseOwnerType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AttachmentOwnerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AttachmentType parseAttachmentType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AttachmentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AttachmentType inferAttachmentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        if (contentType.toLowerCase().startsWith("image/")) {
            return AttachmentType.IMAGE;
        }
        return AttachmentType.FILE;
    }
}
