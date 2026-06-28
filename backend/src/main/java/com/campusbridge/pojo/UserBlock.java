package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBlock {
    private Long id;
    private Long blockerId;
    private Long blockedId;
    private LocalDateTime createdAt;
}
