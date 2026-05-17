package com.campusbridge.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CompletionProof {
    private Long id;
    private Long taskId;
    private Long submitterId;
    private String description;
    private LocalDateTime submittedAt;
}
