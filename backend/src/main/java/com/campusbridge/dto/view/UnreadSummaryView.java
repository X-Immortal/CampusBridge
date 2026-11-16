package com.campusbridge.dto.view;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnreadSummaryView(long total, long system, @JsonProperty("private") long privateCount) {
}
