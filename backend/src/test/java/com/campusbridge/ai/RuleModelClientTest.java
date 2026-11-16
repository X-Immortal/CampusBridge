package com.campusbridge.ai;

import com.campusbridge.gateway.model.ModelUnderstanding;
import com.campusbridge.gateway.model.RuleModelClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 规则基线：该抽的抽出来，没说的绝不许编。 */
class RuleModelClientTest {

    private final RuleModelClient client = new RuleModelClient(AiFixture.CLOCK);

    @Test
    void 课件原句_晚上九点取快递预算八元() {
        ModelUnderstanding understanding = client.extract(
                "我晚上九点下课，想找一个从北门经过5号宿舍楼的人帮我取快递，预算8元", Map.of());

        assertThat(understanding.intent()).isEqualTo("ERRAND_PICKUP");
        assertThat(understanding.slots().get("deadline"))
                .isEqualTo(LocalDateTime.parse("2026-11-05T21:00"));
        assertThat(understanding.slots().get("location")).isEqualTo("北门");
        assertThat(understanding.slots().get("rewardType")).isEqualTo("CASH");
        assertThat(understanding.slots().get("rewardAmount")).isEqualTo(8);
        assertThat(understanding.declaredFields())
                .contains("deadline", "location", "rewardType", "rewardAmount");
    }

    @Test
    void 现场挑战句_三个快递请喝咖啡不暴露宿舍号() {
        ModelUnderstanding understanding = client.extract(
                "我脚受伤了，今晚需要取三个快递，但不希望把宿舍号直接告诉陌生人；我可以请对方喝咖啡，"
                        + "而且希望以后还能联系同一个人", Map.of());

        assertThat(understanding.slots().get("itemCount")).isEqualTo(3);
        assertThat(understanding.slots().get("rewardType")).isEqualTo("NEGOTIABLE");
        assertThat(String.valueOf(understanding.slots().get("rewardDescription"))).contains("咖啡");
        assertThat(understanding.slots().get("shareDormRoom")).isEqualTo(false);
        assertThat(understanding.slots().get("contactPreference")).isEqualTo("FRIEND_FIRST");
        assertThat(understanding.slots().get("riskLevel")).isEqualTo("HIGH");
        assertThat(understanding.slots()).doesNotContainKey("deadline");
        assertThat(understanding.assumptions()).anyMatch(a -> a.contains("今晚"));
    }

    @Test
    void 面议报酬保留用户原话() {
        ModelUnderstanding understanding = client.extract(
                "今晚八点帮我从北门菜鸟驿站取快递，请喝奶茶", Map.of());
        assertThat(understanding.slots().get("rewardType")).isEqualTo("NEGOTIABLE");
        assertThat(String.valueOf(understanding.slots().get("rewardDescription"))).contains("奶茶");
        assertThat(String.valueOf(understanding.slots().get("rewardDescription"))).doesNotContain("咖啡");
    }

    @Test
    void 信息不全时不编地点和时间() {
        ModelUnderstanding understanding = client.extract("帮我取个快递", Map.of());
        assertThat(understanding.slots()).doesNotContainKeys("deadline", "location", "rewardType");
        assertThat(understanding.declaredFields()).doesNotContain("deadline", "location");
    }

    @Test
    void 已经过去的时间顺延到明天并说明理由() {
        ModelUnderstanding understanding = client.extract("早上八点帮我取快递", Map.of());
        assertThat(understanding.slots().get("deadline"))
                .isEqualTo(LocalDateTime.parse("2026-11-06T08:00"));
        assertThat(understanding.assumptions()).anyMatch(a -> a.contains("明天"));
    }
}
