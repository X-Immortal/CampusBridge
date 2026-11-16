package com.campusbridge.ai;

import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import com.campusbridge.policy.PolicyContext;
import com.campusbridge.policy.PolicyEngine;
import com.campusbridge.policy.PolicyResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每条约束都要有一个「正例通过、反例被拦」的测试。
 * 这是课件 02 p63 要求的「每条核心规则 ≥1 正例 +1 反例」在代码里的落地。
 */
class PolicyEngineTest {

    private final PolicyEngine engine = AiFixture.policyEngine(AiFixture.ontology());

    @Test
    void 信息齐全且带确认时全部通过() {
        TaskDraft draft = AiFixture.completeDraft();
        assertThat(confirm(draft, "idem-1", true).clean()).isTrue();
    }

    @Test
    void K01_预算为负被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.REWARD_AMOUNT, -5, TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K01");
    }

    @Test
    void K02_非人民币被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.CURRENCY, "USD", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K02");
    }

    @Test
    void K03_缺时间不能进入确认() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.remove(DraftField.DEADLINE);
        List<String> rules = ruleIds(confirm(draft, "idem-1", true));
        assertThat(rules).contains("K03");
        assertThat(confirm(draft, "idem-1", true).blocked()).isTrue();
    }

    @Test
    void K04_过去的截止时间被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.DEADLINE, "2026-11-04T21:00", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K04");
    }

    @Test
    void K05_深夜任务没做风险披露被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.DEADLINE, "2026-11-05T23:30", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K05");
    }

    @Test
    void K05_做了风险披露就通过() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.DEADLINE, "2026-11-05T23:30", TaskDraft.Source.USER);
        draft.put(DraftField.RISK_DISCLOSURE_CONFIRMED, true, TaskDraft.Source.USER);
        assertThat(confirm(draft, "idem-1", true).clean()).isTrue();
    }

    @Test
    void K06_没有确认令牌不能建单() {
        TaskDraft draft = AiFixture.completeDraft();
        assertThat(ruleIds(confirm(draft, "idem-1", false))).contains("K06");
    }

    @Test
    void K07_出现精确宿舍号但未获授权被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.LOCATION, "5 号楼 502", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K07");
    }

    @Test
    void K07_用户同意共享就通过() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.LOCATION, "5 号楼 502", TaskDraft.Source.USER);
        draft.put(DraftField.SHARE_DORM_ROOM, true, TaskDraft.Source.USER);
        assertThat(confirm(draft, "idem-1", true).clean()).isTrue();
    }

    @Test
    void K08_缺幂等键被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        assertThat(ruleIds(confirm(draft, null, true))).contains("K08");
    }

    @Test
    void K09_标题太短被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.TITLE, "取", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K09");
    }

    @Test
    void K10_完成标准太短被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.COMPLETION_STANDARD, "送到就行", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K10");
    }

    @Test
    void K11_两种联络方式都关被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.ONLY_IN_APP_CONTACT, false, TaskDraft.Source.USER);
        draft.put(DraftField.ALLOW_OFFLINE_CONTACT, false, TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K11");
    }

    @Test
    void K12_本体之外的报酬形态被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.REWARD_TYPE, "COFFEE", TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K12");
    }

    @Test
    void K13_令牌版本落后于当前版本被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        int staleVersion = draft.version() - 1;
        PolicyResult result = engine.evaluate(draft,
                PolicyContext.confirm(AiFixture.now(), "idem-1", true, staleVersion, 1L));
        assertThat(ruleIds(result)).contains("K13");
    }

    @Test
    void K14_一次取十二件被拒() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.ITEM_COUNT, 12, TaskDraft.Source.USER);
        assertThat(ruleIds(confirm(draft, "idem-1", true))).contains("K14");
    }

    @Test
    void K15_推测的位置参与高风险任务时不能替用户决定() {
        TaskDraft draft = AiFixture.completeDraft();
        draft.put(DraftField.RISK_LEVEL, "HIGH", TaskDraft.Source.INFERRED);
        draft.put(DraftField.LOCATION, "北门", TaskDraft.Source.INFERRED);
        PolicyResult result = engine.evaluate(draft,
                PolicyContext.confirm(AiFixture.now(), "idem-1", false, draft.version(), 1L));
        assertThat(ruleIds(result)).contains("K15");
    }

    @Test
    void 澄清阶段的提示不会因为缺令牌而误报K06() {
        TaskDraft draft = AiFixture.completeDraft();
        PolicyResult result = engine.evaluate(draft, PolicyContext.understand(AiFixture.now()));
        assertThat(ruleIds(result)).doesNotContain("K06", "K08", "K13");
    }

    private PolicyResult confirm(TaskDraft draft, String idempotencyKey, boolean withToken) {
        return engine.evaluate(draft, PolicyContext.confirm(
                AiFixture.now(), idempotencyKey, withToken, draft.version(), 1L));
    }

    private List<String> ruleIds(PolicyResult result) {
        return result.violations().stream().map(v -> v.ruleId()).toList();
    }
}
