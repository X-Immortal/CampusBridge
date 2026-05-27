package com.campusbridge.service;

import org.springframework.web.multipart.MultipartFile;

public interface AttachmentService {
    Object uploadAttachment(Long uploaderId, MultipartFile file, String expectedOwnerType, String type);
}
