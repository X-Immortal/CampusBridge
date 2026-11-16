package com.campusbridge.dto.request;

import com.campusbridge.enums.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {
    public PostCategory category;
    public String title;
    public String content;
    public List<Long> attachmentIds;
}
