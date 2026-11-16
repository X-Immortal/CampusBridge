package com.campusbridge.dto.response;

public final class OcrExtractResult {
    private final boolean available;
    private final String realName;
    private final String studentNumber;
    private final String school;

    private OcrExtractResult(boolean available, String realName, String studentNumber, String school) {
        this.available = available;
        this.realName = realName;
        this.studentNumber = studentNumber;
        this.school = school;
    }

    public static OcrExtractResult unavailable() {
        return new OcrExtractResult(false, null, null, null);
    }

    public static OcrExtractResult of(String realName, String studentNumber, String school) {
        return new OcrExtractResult(true, realName, studentNumber, school);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getRealName() {
        return realName;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getSchool() {
        return school;
    }
}
