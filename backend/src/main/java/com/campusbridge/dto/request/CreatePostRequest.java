package com.campusbridge.dto.request;

import com.campusbridge.enums.PostCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    @NotNull(message = "category is required")
    public PostCategory category;

    @NotNull(message = "title is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "title is required")
    @Size(max = 100, message = "title is too long")
    public String title;

    @NotNull(message = "content is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "content is required")
    public String content;

    public List<Long> attachmentIds;
}
