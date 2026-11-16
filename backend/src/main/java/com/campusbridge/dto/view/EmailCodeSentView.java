package com.campusbridge.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

public record EmailCodeSentView(boolean sent, @JsonInclude(JsonInclude.Include.NON_NULL) String debugCode) {
}
