package com.campusbridge.dto.request;

import com.campusbridge.enums.VerificationMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateVerificationRequest {
    public VerificationMethod method;
    public String realName;
    public String studentNumber;
    public String school;
    public String department;
    public String campusEmail;
    public String evidenceUrl;
}
