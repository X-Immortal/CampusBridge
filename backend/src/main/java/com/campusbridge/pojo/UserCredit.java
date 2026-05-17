package com.campusbridge.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredit {
    private Long id;
    private Long userId;
    private Integer points;
    private Integer level;
    private LocalDateTime updatedAt;
}
