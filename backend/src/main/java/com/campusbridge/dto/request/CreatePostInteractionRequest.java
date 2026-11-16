package com.campusbridge.dto.request;

import com.campusbridge.enums.PostInteractionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostInteractionRequest {
    @NotNull(message = "type is required")
    public PostInteractionType type;
}
