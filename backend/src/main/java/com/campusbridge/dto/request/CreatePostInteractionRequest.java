package com.campusbridge.dto.request;

import com.campusbridge.enums.PostInteractionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostInteractionRequest {
    public PostInteractionType type;
}
