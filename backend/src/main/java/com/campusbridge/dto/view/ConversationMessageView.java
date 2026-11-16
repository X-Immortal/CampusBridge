package com.campusbridge.dto.view;

import com.campusbridge.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageView {
    private ConversationView conversation;
    private List<Message> messages;
}
