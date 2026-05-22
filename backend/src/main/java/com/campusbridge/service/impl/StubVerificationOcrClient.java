package com.campusbridge.service.impl;

import com.campusbridge.service.VerificationOcrClient;
import com.campusbridge.utils.AliOcrUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StubVerificationOcrClient implements VerificationOcrClient {

    private static final Pattern STUDENT_NUMBER_PATTERN = Pattern.compile("\\b\\d{8,14}\\b");

    @Autowired
    private AliOcrUtils aliOcrUtils;

    @Override
    public OcrExtractResult extractStudentCard(String evidenceUrl) {
        try {
            JsonNode data = aliOcrUtils.recognizeAllText(evidenceUrl);
            String content = extractContent(data);
            if (!StringUtils.hasText(content)) {
                return OcrExtractResult.unavailable();
            }

            String realName = findName(content);
            String studentNumber = findStudentNumber(content);
            String school = findSchool(content);
            if (!StringUtils.hasText(realName) || !StringUtils.hasText(studentNumber) || !StringUtils.hasText(school)) {
                return OcrExtractResult.unavailable();
            }
            return OcrExtractResult.of(realName, studentNumber, school);
        } catch (Exception e) {
            return OcrExtractResult.unavailable();
        }
    }

    private String extractContent(JsonNode data) {
        if (data == null) {
            return null;
        }
        if (data.hasNonNull("content")) {
            return data.get("content").asText();
        }
        return data.toString();
    }

    private String findName(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String normalized = line.replace("：", ":").trim();
            if (normalized.startsWith("姓名:")) {
                String value = normalized.substring("姓名:".length()).trim();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String findStudentNumber(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String normalized = line.replace("：", ":").trim();
            if (normalized.contains("学号")) {
                Matcher matcher = STUDENT_NUMBER_PATTERN.matcher(normalized);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        Matcher fallback = STUDENT_NUMBER_PATTERN.matcher(content);
        if (fallback.find()) {
            return fallback.group();
        }
        return null;
    }

    private String findSchool(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.contains("大学") || trimmed.contains("学院")) {
                return trimmed;
            }
        }
        return null;
    }
}
