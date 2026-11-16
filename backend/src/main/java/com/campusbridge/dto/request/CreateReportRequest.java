package com.campusbridge.dto.request;

import com.campusbridge.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    @NotNull(message = "targetType is required")
    public ReportTargetType targetType;

    @NotNull(message = "targetId is required")
    public Long targetId;

    @NotNull(message = "reason is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "reason is required")
    @Size(max = 100, message = "reason is too long")
    public String reason;

    @Size(max = 1000, message = "description is too long")
    public String description;
}
