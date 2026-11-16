package com.campusbridge.service.impl;

import com.campusbridge.dto.ModerationMatch;
import com.campusbridge.enums.ContentItemType;
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
    private static final List<String> PROHIBITED_KEYWORDS = List.of(
            "代考", "替考", "代签到", "代出勤", "代上课", "代课", "签到", "作弊",
            "刷课", "买卖答案", "赌博", "诈骗", "校园贷", "管制刀具", "处方药");
    private static final List<String> CONTACT_KEYWORDS = List.of(
            "微信", "vx", "qq", "电话", "手机号", "加我");

    @Override
    public void assertPostPublishAllowed(
            Long operatorId,
            CreatePostRequest request) {
        if (request == null) {
            return;
        }

        assertPublishAllowed(operatorId, ContentItemType.POST, buildPostContent(request.getTitle(), request.getContent()), true);
    }

    @Override
    public void assertPostUpdateAllowed(Long operatorId, UpdatePostRequest request) {
        assertPublishAllowed(operatorId, ContentItemType.POST,
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
                ContentItemType.TASK,
                content,
                defaultBoolean(request.getOnlyInAppContact(), true));
    }

    @Override
    public void assertTaskUpdateAllowed(
            Long operatorId, UpdateTaskRequest request, String sensitiveReason) {
        String content = buildTaskContent(request.getTitle(), request.getDescription(),
                request.getRewardDescription(), request.getCompletionStandard(), sensitiveReason);
        assertPublishAllowed(operatorId, ContentItemType.TASK, content, request.getOnlyInAppContact());
    }

    private void assertPublishAllowed(
            Long operatorId,
            ContentItemType targetType,
            String content,
            boolean blockContactLeak) {
        String ruleCode = "PROHIBITED_KEYWORD";
        ModerationMatch hit = findMatch(content, PROHIBITED_KEYWORDS);
        if (hit == null && blockContactLeak) {
            ruleCode = "CONTACT_LEAK";
            hit = findMatch(content, CONTACT_KEYWORDS);
        }
        if (hit == null) {
            return;
        }

        String excerpt = buildExcerpt(content, hit.index());
        log.warn(
                "Content blocked: targetType={}, operatorId={}, ruleCode={}, keyword={}, excerpt={}",
                targetType,
                operatorId,
                ruleCode,
                hit.keyword(),
                excerpt);

        throw new BusinessException(
                ErrorCode.CONTENT_001_BLOCKED,
                ErrorCode.CONTENT_001_BLOCKED.getDefaultMessage(),
                List.of("ruleCode=" + ruleCode, "keyword=" + hit.keyword()));
    }

    private ModerationMatch findMatch(String content, List<String> keywords) {
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            int index = normalizedContent.indexOf(keyword.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                return new ModerationMatch(keyword, index);
            }
        }
        return null;
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
}
