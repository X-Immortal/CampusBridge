package com.campusbridge.dto.request;

import com.campusbridge.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveReportRequest {
    @NotNull(message = "status is required")
    public ReportStatus status;

    @NotNull(message = "decision is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "decision is required")
    @Size(max = 500, message = "decision is too long")
    public String decision;

    public Long punishedUserId;

    public Integer creditChange;

    public Boolean markTaskDisputed;
}
