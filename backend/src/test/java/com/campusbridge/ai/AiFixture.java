package com.campusbridge.ai;

import com.campusbridge.config.AiProperties;
import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.ontology.OntologyRegistry;
import com.campusbridge.policy.ConstraintEvaluators;
import com.campusbridge.policy.PolicyEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** M1 测试共用的装配。所有时间固定在 2026-11-05 09:30（Asia/Shanghai），测试才可重复。 */
final class AiFixture {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T01:30:00Z"), ZONE);
    static final String NOW = "2026-11-05T09:30:00";

    private AiFixture() {
    }

    static AiProperties properties(String mode) {
        AiProperties properties = new AiProperties();
        properties.getModel().setMode(mode);
        properties.getToken().setSecret("test-secret-for-hmac-do-not-use-in-prod");
        properties.getToken().setTtlSeconds(300);
        return properties;
    }

    static OntologyRegistry ontology() {
        return new OntologyRegistry(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "classpath:ontology/campus-ontology.v1.json",
                new ConstraintEvaluators());
    }

    static PolicyEngine policyEngine(OntologyRegistry ontology) {
        return new PolicyEngine(ontology, new ConstraintEvaluators());
    }

    /** 一份「什么都齐了」的草稿：好让每个测试只改它关心的那一个字段。 */
    static TaskDraft completeDraft() {
        TaskDraft draft = new TaskDraft();
        draft.put(DraftField.GOAL, "今晚九点去北门菜鸟驿站帮我取三个快递，预算 8 元",
                TaskDraft.Source.USER);
        draft.put(DraftField.TITLE, "帮取 3 个快递（北门菜鸟驿站）", TaskDraft.Source.USER);
        draft.put(DraftField.CATEGORY, "ERRAND", TaskDraft.Source.USER);
        draft.put(DraftField.LOCATION, "北门菜鸟驿站", TaskDraft.Source.USER);
        draft.put(DraftField.DEADLINE, "2026-11-05T21:00", TaskDraft.Source.USER);
        draft.put(DraftField.REWARD_TYPE, "CASH", TaskDraft.Source.USER);
        draft.put(DraftField.REWARD_AMOUNT, 8, TaskDraft.Source.USER);
        draft.put(DraftField.CURRENCY, "CNY", TaskDraft.Source.DEFAULT);
        draft.put(DraftField.COMPLETION_STANDARD, "放到 5 号楼一层货架并拍照确认", TaskDraft.Source.USER);
        draft.put(DraftField.REQUIRES_PROOF, true, TaskDraft.Source.DEFAULT);
        draft.put(DraftField.ONLY_IN_APP_CONTACT, true, TaskDraft.Source.DEFAULT);
        draft.put(DraftField.ALLOW_OFFLINE_CONTACT, false, TaskDraft.Source.DEFAULT);
        draft.put(DraftField.RISK_LEVEL, "LOW", TaskDraft.Source.INFERRED);
        draft.put(DraftField.ITEM_COUNT, 3, TaskDraft.Source.USER);
        return draft;
    }

    static LocalDateTime now() {
        return LocalDateTime.parse(NOW);
    }
}
