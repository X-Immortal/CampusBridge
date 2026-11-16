package com.campusbridge.dto.view;

import com.campusbridge.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserView {
    private Long id;
    private String username;
    private UserRole role;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
