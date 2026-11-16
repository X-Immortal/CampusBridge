package com.campusbridge.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

public record EmailChangeCodeSentView(String email, boolean sent, @JsonInclude(JsonInclude.Include.NON_NULL) String debugCode) {
}
