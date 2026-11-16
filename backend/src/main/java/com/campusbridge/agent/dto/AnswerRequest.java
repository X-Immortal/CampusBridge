package com.campusbridge.agent.dto;

import java.util.List;

/** POST /api/assistant/sessions/{id}/answers。一条回答只能改一个字段。 */
public record AnswerRequest(List<AnswerInput> answers) {

    public record AnswerInput(String field, Object value) {
    }
}
