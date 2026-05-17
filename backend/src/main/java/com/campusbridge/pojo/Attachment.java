package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    private Long id;
    private Long uploaderId;
    private String ownerType;
    private Long ownerId;
    private String type;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private Long size;
    private String status;
    private LocalDateTime createdAt;
}
