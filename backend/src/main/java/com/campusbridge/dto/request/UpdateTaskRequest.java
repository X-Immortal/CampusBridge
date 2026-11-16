package com.campusbridge.dto.request;

import com.campusbridge.enums.RewardType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {
    @NotNull(message = "title is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "title is required")
    @Size(max = 100, message = "title is too long")
    public String title;

    @NotNull(message = "description is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "description is required")
    @Size(max = 2000, message = "description is too long")
    public String description;

    @Future(message = "deadline must be in the future")
    @NotNull(message = "deadline is required")
    public LocalDateTime deadline;

    @NotNull(message = "rewardType is required")
    public RewardType rewardType;

    @Size(max = 200, message = "rewardDescription is too long")
    public String rewardDescription;

    @Positive(message = "rewardAmount must be positive")
    public Integer rewardAmount;

    @NotNull(message = "completionStandard is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "completionStandard is required")
    @Size(max = 500, message = "completionStandard is too long")
    public String completionStandard;

    @NotNull(message = "requiresProof is required")
    public Boolean requiresProof;

    @NotNull(message = "onlyInAppContact is required")
    public Boolean onlyInAppContact;

    @NotNull(message = "allowOfflineContact is required")
    public Boolean allowOfflineContact;
}
