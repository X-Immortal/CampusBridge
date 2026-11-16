package com.campusbridge.ai;

import com.campusbridge.domain.TaskDraft;
import com.campusbridge.schema.CardAction;
import com.campusbridge.schema.CardBlock;
import com.campusbridge.schema.CardFactory;
import com.campusbridge.schema.ChoiceOption;
import com.campusbridge.schema.SchemaValidator;
import com.campusbridge.schema.SummaryRow;
import com.campusbridge.schema.TokenView;
import com.campusbridge.schema.UiCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 白名单与绑定校验：三条硬规则各一组正例反例。 */
class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();
    private final CardFactory cardFactory = new CardFactory();

    @Test
    void 工厂生成的确认卡一定通过校验() {
        TaskDraft draft = AiFixture.completeDraft();
        String digest = cardFactory.digestOf(draft);
        UiCard card = cardFactory.confirmation("c-1", "s-1", draft.version(), draft, digest,
                new TokenView("v1.nonce.1.sig", "2026-11-05T10:00:00Z", "nonce", digest),
                List.of(), "rule");
        assertThat(validator.validate(card).errors()).isEmpty();
    }

    @Test
    void 非白名单组件整张卡被拒() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "confirmation", "标题",
                List.of(scriptBlock()), List.of(CardAction.primary("cancel", "取消")),
                List.of(), null, null, "rule");
        assertThat(validator.validate(card).errors())
                .anyMatch(e -> e.contains("非白名单组件被拒绝：Script"));
    }

    @Test
    void 绑定不存在的字段被拒() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "clarification", "标题",
                List.of(CardBlock.field("详细地址", "详细地址", "", "", true)),
                List.of(CardAction.primary("answer", "提交")), List.of(), null, null, "rule");
        assertThat(validator.validate(card).errors())
                .anyMatch(e -> e.contains("绑定了不存在的字段：详细地址"));
    }

    @Test
    void 高风险动作没有要求确认被拒() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "confirmation", "标题",
                List.of(CardBlock.summary(List.of(new SummaryRow("地点", "北门", "用户说的")))),
                List.of(CardAction.primary("confirm", "确认发布")), List.of(), "d", null, "rule");
        assertThat(validator.validate(card).errors())
                .anyMatch(e -> e.contains("没有要求确认"));
    }

    @Test
    void 确认动作没有令牌被拒() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "confirmation", "标题",
                List.of(CardBlock.summary(List.of(new SummaryRow("地点", "北门", "用户说的")))),
                List.of(CardAction.confirm("confirm", "确认发布")), List.of(), "d", null, "rule");
        assertThat(validator.validate(card).errors()).contains("确认动作没有令牌");
    }

    @Test
    void 卡片展示值与令牌摘要不一致被拒() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "confirmation", "标题",
                List.of(CardBlock.summary(List.of(new SummaryRow("地点", "北门", "用户说的")))),
                List.of(CardAction.confirm("confirm", "确认发布")), List.of(), "digest-A",
                new TokenView("v1.x.1.y", "2026-11-05T10:00:00Z", "nonce", "digest-B"), "rule");
        assertThat(validator.validate(card).errors())
                .anyMatch(e -> e.contains("参数与令牌摘要不一致"));
    }

    @Test
    void 比较卡选项不能为空() {
        UiCard card = new UiCard(UiCard.SCHEMA_VERSION, "c-1", "s-1", null, 1, "comparison", "标题",
                List.of(CardBlock.choice("matchMode", List.of(new ChoiceOption("now", "立即匹配", "")), "now"),
                        CardBlock.summary(List.of(new SummaryRow("地点", "北门", "用户说的")))),
                List.of(CardAction.primary("select", "选这个")), List.of(), null, null, "rule");
        assertThat(validator.validate(card).valid()).isTrue();
    }

    private CardBlock scriptBlock() {
        return new CardBlock("Script", "alert(1)", null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
