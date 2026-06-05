package com.campusbridge.service.impl;

import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.mapper.AttachmentMapper;
import com.campusbridge.pojo.Attachment;
import com.campusbridge.service.AttachmentService;
import com.campusbridge.utils.AliOSSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private AliOSSUtils aliOSSUtils;

    @Override
    public Object uploadAttachment(Long uploaderId, MultipartFile file, String expectedOwnerType, String type) {
        if (uploaderId == null || file == null || file.isEmpty()) {
            return null;
        }

        ApiEnums.AttachmentOwnerType ownerTypeEnum = parseOwnerType(expectedOwnerType);
        if (StringUtils.hasText(expectedOwnerType) && ownerTypeEnum == null) {
            return -2L;
        }

        ApiEnums.AttachmentType attachmentType = parseAttachmentType(type);
        if (attachmentType == null) {
            attachmentType = inferAttachmentType(file.getContentType());
        }
        if (attachmentType == null) {
            return -3L;
        }

        try {
            String fileUrl = aliOSSUtils.upload(file);

            Attachment entity = new Attachment();
            entity.setUploaderId(uploaderId);
            entity.setOwnerType(ownerTypeEnum == null ? null : ownerTypeEnum.name());
            entity.setOwnerId(null);
            entity.setType(attachmentType.name());
            entity.setFileName(file.getOriginalFilename());
            entity.setFileUrl(fileUrl);
            entity.setContentType(file.getContentType());
            entity.setSize(file.getSize());
            entity.setStatus(ApiEnums.AttachmentStatus.TEMPORARY.name());
            entity.setCreatedAt(LocalDateTime.now());

            int affected = attachmentMapper.insert(entity);
            if (affected <= 0) {
                return -5L;
            }
            return entity;
        } catch (Exception e) {
            LOGGER.error("Attachment upload failed: uploaderId={}, expectedOwnerType={}, type={}",
                    uploaderId, expectedOwnerType, type, e);
            return -4L;
        }
    }

    private ApiEnums.AttachmentOwnerType parseOwnerType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ApiEnums.AttachmentOwnerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ApiEnums.AttachmentType parseAttachmentType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ApiEnums.AttachmentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ApiEnums.AttachmentType inferAttachmentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        if (contentType.toLowerCase().startsWith("image/")) {
            return ApiEnums.AttachmentType.IMAGE;
        }
        return ApiEnums.AttachmentType.FILE;
    }
}
