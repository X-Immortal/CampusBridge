package com.campusbridge.service.impl;

import com.campusbridge.common.BusinessException;
import com.campusbridge.common.ErrorCode;
import com.campusbridge.dto.request.CreatePostRequest;
import com.campusbridge.dto.request.CreateTaskRequest;
import com.campusbridge.dto.request.UpdatePostRequest;
import com.campusbridge.dto.request.UpdateTaskRequest;
import com.campusbridge.service.ContentModerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class ContentModerationServiceImpl implements ContentModerationService {
    private static final int EXCERPT_LENGTH = 120;
    private static final String TARGET_TYPE_POST = "POST";
    private static final String TARGET_TYPE_TASK = "TASK";
    private static final String RULE_PROHIBITED_KEYWORD = "PROHIBITED_KEYWORD";
    private static final String RULE_CONTACT_LEAK = "CONTACT_LEAK";

    private static final List<Rule> PROHIBITED_RULES = List.of(
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4ee3\u8003"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u66ff\u8003"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4ee3\u7b7e\u5230"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4ee3\u51fa\u52e4"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4ee3\u4e0a\u8bfe"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4ee3\u8bfe"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u7b7e\u5230"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4f5c\u5f0a"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u5237\u8bfe"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u4e70\u5356\u7b54\u6848"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u8d4c\u535a"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u8bc8\u9a97"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u6821\u56ed\u8d37"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u7ba1\u5236\u5200\u5177"),
            new Rule(RULE_PROHIBITED_KEYWORD, "\u5904\u65b9\u836f"));

    private static final List<Rule> CONTACT_RULES = List.of(
            new Rule(RULE_CONTACT_LEAK, "\u5fae\u4fe1"),
            new Rule(RULE_CONTACT_LEAK, "vx"),
            new Rule(RULE_CONTACT_LEAK, "qq"),
            new Rule(RULE_CONTACT_LEAK, "\u7535\u8bdd"),
            new Rule(RULE_CONTACT_LEAK, "\u624b\u673a\u53f7"),
            new Rule(RULE_CONTACT_LEAK, "\u52a0\u6211"));

    @Override
    public void assertPostPublishAllowed(
            Long operatorId,
            CreatePostRequest request) {
        if (request == null) {
            return;
        }

        assertPublishAllowed(operatorId, TARGET_TYPE_POST, buildPostContent(request.getTitle(), request.getContent()), true);
    }

    @Override
    public void assertPostUpdateAllowed(Long operatorId, UpdatePostRequest request) {
        assertPublishAllowed(operatorId, TARGET_TYPE_POST,
                buildPostContent(request.getTitle(), request.getContent()), true);
    }

    @Override
    public void assertTaskPublishAllowed(
            Long operatorId,
            CreateTaskRequest request) {
        if (request == null) {
            return;
        }

        String content = buildTaskContent(request.getTitle(), request.getDescription(),
                request.getRewardDescription(), request.getCompletionStandard(), request.getSensitiveReason());
        assertPublishAllowed(
                operatorId,
                TARGET_TYPE_TASK,
                content,
                defaultBoolean(request.getOnlyInAppContact(), true));
    }

    @Override
    public void assertTaskUpdateAllowed(
            Long operatorId, UpdateTaskRequest request, String sensitiveReason) {
        String content = buildTaskContent(request.getTitle(), request.getDescription(),
                request.getRewardDescription(), request.getCompletionStandard(), sensitiveReason);
        assertPublishAllowed(operatorId, TARGET_TYPE_TASK, content, request.getOnlyInAppContact());
    }

    private void assertPublishAllowed(
            Long operatorId,
            String targetType,
            String content,
            boolean blockContactLeak) {
        Hit hit = findHit(content, PROHIBITED_RULES);
        if (hit == null && blockContactLeak) {
            hit = findHit(content, CONTACT_RULES);
        }
        if (hit == null) {
            return;
        }

        String excerpt = buildExcerpt(content, hit.index());
        log.warn(
                "Content blocked: targetType={}, operatorId={}, ruleCode={}, keyword={}, excerpt={}",
                targetType,
                operatorId,
                hit.ruleCode(),
                hit.keyword(),
                excerpt);

        throw new BusinessException(
                ErrorCode.CONTENT_001_BLOCKED,
                ErrorCode.CONTENT_001_BLOCKED.getDefaultMessage(),
                List.of("ruleCode=" + hit.ruleCode(), "keyword=" + hit.keyword()));
    }

    private String buildPostContent(String title, String content) {
        StringBuilder builder = new StringBuilder();
        append(builder, title);
        append(builder, content);
        return builder.toString();
    }

    private String buildTaskContent(String... fields) {
        StringBuilder builder = new StringBuilder();
        for (String field : fields) {
            append(builder, field);
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    private Hit findHit(String content, List<Rule> rules) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        for (Rule rule : rules) {
            String normalizedKeyword = rule.keyword().toLowerCase(Locale.ROOT);
            int index = normalizedContent.indexOf(normalizedKeyword);
            if (index >= 0) {
                return new Hit(rule.ruleCode(), rule.keyword(), index);
            }
        }
        return null;
    }

    private String buildExcerpt(String content, int hitIndex) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int safeHitIndex = Math.max(0, Math.min(hitIndex, content.length()));
        int start = Math.max(0, safeHitIndex - EXCERPT_LENGTH / 2);
        int end = Math.min(content.length(), start + EXCERPT_LENGTH);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private record Rule(String ruleCode, String keyword) {
    }

    private record Hit(String ruleCode, String keyword, int index) {
    }
}
