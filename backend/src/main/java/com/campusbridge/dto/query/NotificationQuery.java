package com.campusbridge.dto.query;

import com.campusbridge.enums.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Set;

@Data
public class NotificationQuery {
    @Min(1)
    private int page = 1;
    @Min(1) @Max(100)
    private int size = 20;
    private boolean unreadOnly;
    private Set<NotificationType> types;
    @Size(max = 100)
    private String keyword;
}
