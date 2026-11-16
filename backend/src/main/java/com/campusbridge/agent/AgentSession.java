package com.campusbridge.agent;

import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.schema.UiCard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次对话会话。M1 里它只在内存中：进程重启就没了（M2 会用游标 + 快照补上恢复）。
 *
 * <p>状态机刻意很窄：{@code CLARIFYING → PROPOSING → AWAITING_CONFIRMATION → CREATED}，
 * 另有 {@code CANCELLED}。任何一步都不能跳过 Policy 直接到 CREATED。</p>
 */
public final class AgentSession {

    public static final String CLARIFYING = "CLARIFYING";
    public static final String PROPOSING = "PROPOSING";
    public static final String AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";
    public static final String CREATED = "CREATED";
    public static final String CANCELLED = "CANCELLED";

    private final String id;
    private final String traceId;
    private final Long requesterId;
    private final TaskDraft draft = new TaskDraft();
    private final Instant createdAt;

    private String state = CLARIFYING;
    private String intent = "ERRAND";
    private double confidence;
    private List<String> assumptions = new ArrayList<>();
    private String modelMode = "rule";
    private boolean degraded;
    private List<ClarificationQuestion> questions = List.of();
    private String selectedOptionId;
    private String tokenValue;
    private Long taskId;
    private UiCard lastCard;
    private int cardSequence;
    private Instant updatedAt;

    public AgentSession(String id, String traceId, Long requesterId, Instant createdAt) {
        this.id = id;
        this.traceId = traceId;
        this.requesterId = requesterId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String nextCardId() {
        return id + "-card-" + (++cardSequence);
    }

    public String id() {
        return id;
    }

    public String traceId() {
        return traceId;
    }

    public Long requesterId() {
        return requesterId;
    }

    public TaskDraft draft() {
        return draft;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String state() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public String intent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public double confidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<String> assumptions() {
        return assumptions;
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions == null ? new ArrayList<>() : new ArrayList<>(assumptions);
    }

    public String modelMode() {
        return modelMode;
    }

    public void setModelMode(String modelMode) {
        this.modelMode = modelMode;
    }

    public boolean degraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public List<ClarificationQuestion> questions() {
        return questions;
    }

    public void setQuestions(List<ClarificationQuestion> questions) {
        this.questions = questions == null ? List.of() : questions;
    }

    public String selectedOptionId() {
        return selectedOptionId;
    }

    public void setSelectedOptionId(String selectedOptionId) {
        this.selectedOptionId = selectedOptionId;
    }

    public String tokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public Long taskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public UiCard lastCard() {
        return lastCard;
    }

    public void setLastCard(UiCard lastCard) {
        this.lastCard = lastCard;
        this.updatedAt = Instant.now();
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
