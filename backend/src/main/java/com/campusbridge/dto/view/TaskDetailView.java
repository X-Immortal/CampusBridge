package com.campusbridge.dto.view;

import com.campusbridge.entity.Attachment;
import com.campusbridge.entity.CompletionProof;
import com.campusbridge.entity.Review;
import com.campusbridge.entity.TaskApplication;
import com.campusbridge.entity.TaskHistory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailView {
    private TaskView task;
    private List<TaskApplication> applications;
    private CompletionProof completionProof;
    private List<Attachment> attachments;
    private List<TaskHistory> history;
    private List<Review> reviews;
}
