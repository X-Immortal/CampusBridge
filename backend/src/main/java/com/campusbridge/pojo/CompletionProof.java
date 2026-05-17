package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompletionProof {
    private Long id;
    private Long taskId;
    private Long submitterId;
    private String description;
    private LocalDateTime submittedAt;
}
