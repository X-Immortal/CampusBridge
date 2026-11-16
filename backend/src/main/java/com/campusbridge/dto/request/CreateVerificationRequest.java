package com.campusbridge.dto.request;

import com.campusbridge.enums.VerificationMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateVerificationRequest {
    @NotNull(message = "method is required")
    public VerificationMethod method;

    @NotNull(message = "realName is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "realName is required")
    public String realName;

    @NotNull(message = "studentNumber is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "studentNumber is required")
    public String studentNumber;

    @NotNull(message = "school is required")
    @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "school is required")
    public String school;

    public String department;

    public String campusEmail;

    public String evidenceUrl;

    @JsonIgnore
    @AssertTrue(message = "evidenceUrl is required for document verification")
    public boolean isEvidenceProvided() {
        return (method != VerificationMethod.STUDENT_CARD_IMAGE && method != VerificationMethod.MANUAL_REVIEW)
                || (evidenceUrl != null && !evidenceUrl.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "campusEmail must be an edu.cn address")
    public boolean isCampusEmailValid() {
        return method != VerificationMethod.CAMPUS_EMAIL
                || (campusEmail != null && campusEmail.trim().matches("(?i)^[^@\\s]+@[^@\\s]*\\.edu\\.cn$"));
    }
}
