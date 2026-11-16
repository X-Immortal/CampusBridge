package com.campusbridge.dto.request;

import com.campusbridge.enums.PostStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {
    public String title;
    public String content;
    public PostStatus status;
}
