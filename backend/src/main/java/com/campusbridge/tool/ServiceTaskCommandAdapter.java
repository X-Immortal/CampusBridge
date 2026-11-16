package com.campusbridge.tool;

import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.dto.enums.ApiEnums;
import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.dto.view.TaskView;
import com.campusbridge.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 把任务草稿翻译成 1.0 的 {@code CreateTaskRequest}，再交给原来的 {@code TaskService}。
 *
 * <p>这一层刻意很薄：2.0 不加新业务，只把「用户确认过的东西」按旧语义写进旧表。
 * 1.0 的校验（长度、截止时间、报酬、内容治理）照旧生效，所以 2.0 不可能绕过旧规则。</p>
 */
@Component
public class ServiceTaskCommandAdapter implements TaskCommandPort {

    private static final String DEFAULT_CAMPUS = "主校区";

    private final TaskService taskService;

    public ServiceTaskCommandAdapter(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public Long createTask(Long requesterId, TaskDraft draft) {
        RequestDtos.CreateTaskRequest request = new RequestDtos.CreateTaskRequest();
        request.category = categoryOf(draft.text(DraftField.CATEGORY));
        request.title = draft.text(DraftField.TITLE);
        request.description = description(draft);
        request.campus = valueOr(draft.text(DraftField.CAMPUS), DEFAULT_CAMPUS);
        request.location = draft.text(DraftField.LOCATION);
        request.deliveryLocation = draft.text(DraftField.DELIVERY_LOCATION);
        request.deadline = draft.dateTime(DraftField.DEADLINE);
        request.rewardType = rewardTypeOf(draft.text(DraftField.REWARD_TYPE));
        request.rewardDescription = draft.text(DraftField.REWARD_DESCRIPTION);
        request.rewardAmount = draft.integer(DraftField.REWARD_AMOUNT);
        request.completionStandard = draft.text(DraftField.COMPLETION_STANDARD);
        request.requiresProof = flagOr(draft.flag(DraftField.REQUIRES_PROOF), true);
        request.onlyInAppContact = flagOr(draft.flag(DraftField.ONLY_IN_APP_CONTACT), true);
        request.allowOfflineContact = flagOr(draft.flag(DraftField.ALLOW_OFFLINE_CONTACT), false);
        request.isInstantTask = false;
        request.riskDisclosureConfirmed = flagOr(draft.flag(DraftField.RISK_DISCLOSURE_CONFIRMED), false);
        request.sensitiveReason = "HIGH".equalsIgnoreCase(draft.text(DraftField.RISK_LEVEL))
                ? "深夜 / 上门 / 涉及隐私位置" : null;
        TaskView task = taskService.createTask(requesterId, request);
        return task.getId();
    }

    private String description(TaskDraft draft) {
        String goal = draft.text(DraftField.GOAL);
        String standard = draft.text(DraftField.COMPLETION_STANDARD);
        StringBuilder builder = new StringBuilder();
        builder.append(goal == null || goal.isBlank() ? draft.text(DraftField.TITLE) : goal);
        if (standard != null && !standard.isBlank()) {
            builder.append("\n完成标准：").append(standard);
        }
        return builder.toString();
    }

    private ApiEnums.TaskCategory categoryOf(String value) {
        try {
            return ApiEnums.TaskCategory.valueOf(valueOr(value, "ERRAND").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ApiEnums.TaskCategory.ERRAND;
        }
    }

    private ApiEnums.RewardType rewardTypeOf(String value) {
        try {
            return ApiEnums.RewardType.valueOf(valueOr(value, "NEGOTIABLE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ApiEnums.RewardType.NEGOTIABLE;
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Boolean flagOr(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
