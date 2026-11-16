package com.campusbridge.service;

import com.campusbridge.entity.Attachment;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    Attachment uploadAttachment(Long uploaderId, MultipartFile file, String expectedOwnerType, String type);
}
