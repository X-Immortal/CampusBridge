package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.entity.Attachment;
import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentStatus;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.utils.AliOSSUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final AttachmentMapper attachmentMapper;
    private final AliOSSUtils aliOSSUtils;

    @Override
    public Attachment uploadAttachment(Long uploaderId, MultipartFile file, AttachmentOwnerType expectedOwnerType) {
        if (uploaderId == null || file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTACH_001_INVALID_INPUT);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.FILE_002_UNSUPPORTED_TYPE, "仅支持 PNG、JPEG 或 WebP 图片");
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
        entity.setOwnerType(expectedOwnerType);
        entity.setOwnerId(null);
        entity.setFileName(file.getOriginalFilename());
        entity.setFileUrl(fileUrl);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        entity.setStatus(AttachmentStatus.TEMPORARY);
        entity.setCreatedAt(LocalDateTime.now());

        int affected = attachmentMapper.insert(entity);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.ATTACH_005_SAVE_FAILED);
        }
        return entity;
    }

}
