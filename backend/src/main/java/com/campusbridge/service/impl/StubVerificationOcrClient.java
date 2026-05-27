package com.campusbridge.service.impl;

import com.campusbridge.service.VerificationOcrClient;
import com.campusbridge.utils.AliOcrUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StubVerificationOcrClient implements VerificationOcrClient {

    private static final Pattern STUDENT_NUMBER_PATTERN = Pattern.compile("\\b\\d{8,14}\\b");
    private static final Pattern NAME_LABEL_PATTERN = Pattern.compile(
            "(?:\\u59d3\\u540d|name)\\s*[:\\uff1a]?\\s*([\\p{IsHan}A-Za-z\\u00b7]{2,30})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STUDENT_NUMBER_LABEL_PATTERN = Pattern.compile(
            "(?:\\u5b66\\u53f7|student\\s*id|id\\s*no)\\s*[:\\uff1a]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHOOL_KEYWORD_PATTERN = Pattern.compile(
            "(?:\\u5927\\u5b66|\\u5b66\\u9662|university|college|school)",
            Pattern.CASE_INSENSITIVE);

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
            if (!StringUtils.hasText(realName) && !StringUtils.hasText(studentNumber) && !StringUtils.hasText(school)) {
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
        StringBuilder sb = new StringBuilder();
        if (data.hasNonNull("content")) {
            appendText(sb, data.get("content").asText());
        }
        collectText(data, sb);

        String merged = sb.toString().trim();
        return StringUtils.hasText(merged) ? merged : data.toString();
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            appendText(sb, node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectText(item, sb);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectText(field.getValue(), sb);
            }
        }
    }

    private void appendText(StringBuilder sb, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append(text.trim());
    }

    private String findName(String content) {
        Matcher labelMatcher = NAME_LABEL_PATTERN.matcher(content);
        if (labelMatcher.find()) {
            String value = labelMatcher.group(1);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String findStudentNumber(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String normalized = line.trim();
            if (STUDENT_NUMBER_LABEL_PATTERN.matcher(normalized).find()) {
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
            if (SCHOOL_KEYWORD_PATTERN.matcher(trimmed).find()) {
                return trimmed;
            }
        }
        return null;
    }
}
