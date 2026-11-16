package com.campusbridge.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCompletionRequest {
    public String description;
    public List<Long> attachmentIds;
}
