package com.campusbridge.dto.request;

import com.campusbridge.enums.AttachmentOwnerType;
import com.campusbridge.enums.AttachmentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadAttachmentRequest {
    public MultipartFile file;
    public AttachmentOwnerType expectedOwnerType;
    public AttachmentType type;
}
